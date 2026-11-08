package expo.modules.eventcliprender

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

/**
 * Media3 Transformer export with application-Looper access for create/start/cancel.
 * Completion wait happens on the calling (worker) thread — never blocks the app Looper.
 *
 * Real Media3 export requires a device/emulator; JVM tests use [TransformerOps] fakes.
 */
@UnstableApi
class TransformerExport(
  private val gate: AppLooperGate,
  private val ops: TransformerOps = RealTransformerOps(),
) {
  @Volatile private var activeCancel: (() -> Unit)? = null
  private val cancelRequested = AtomicBoolean(false)

  fun requestCancel() {
    cancelRequested.set(true)
    val c = activeCancel
    if (c != null) {
      try {
        gate.runOnAppLooper { c.invoke() }
      } catch (_: Throwable) {
        // best-effort cancel
      }
    }
  }

  fun isCancelRequested(): Boolean = cancelRequested.get()

  fun resetCancel() {
    cancelRequested.set(false)
  }

  fun export(
    context: Context,
    request: ComposeRequest,
    watermarkBitmap: Bitmap,
    timeoutMinutes: Long = 25,
  ): ExportOutcome {
    cancelRequested.set(false)
    val outPath = request.outputUri.removePrefix("file://")
    val outFile = File(outPath)
    outFile.parentFile?.mkdirs()
    require(!outFile.exists()) { "Output already exists; use a distinct run output to preserve prior files" }
    if (request.clips.isEmpty()) throw IllegalArgumentException("clips empty")

    val ducks =
      request.duckRanges.map { DuckRange(it.startSec, it.endSec, it.gain) }.let { DuckEnvelope.sanitize(it) }
    val duckProcessor = VolumeEnvelopeAudioProcessor(ducks, request.duckRampSec)
    val watermarkOverlay: TextureOverlay = BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap)
    val overlayEffect = OverlayEffect(listOf(watermarkOverlay))
    val presentation =
      Presentation.createForWidthAndHeight(request.width, request.height, Presentation.LAYOUT_SCALE_TO_FIT)
    val audioFx = Effects(listOf(duckProcessor), emptyList())
    val videoFx = Effects(emptyList(), listOf(presentation, overlayEffect))

    val videoItems = ArrayList<EditedMediaItem>()
    var hasImageWithoutAudio = false
    var videoClipsClipped = 0
    for (clip in request.clips) {
      val uri = Uri.parse(normalizeUri(clip.uri))
      if (clip.type == "image") {
        hasImageWithoutAudio = true
        val durMs = (((clip.durationSec ?: 1.0) * 1000.0).roundToLong()).coerceAtLeast(1L)
        val durUs = durMs * 1000L
        val mime =
          when {
            clip.uri.endsWith(".png", true) -> MimeTypes.IMAGE_PNG
            clip.uri.endsWith(".webp", true) -> MimeTypes.IMAGE_WEBP
            else -> MimeTypes.IMAGE_JPEG
          }
        val mediaItem =
          MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mime)
            .setImageDurationMs(durMs)
            .build()
        videoItems.add(
          EditedMediaItem.Builder(mediaItem)
            .setEffects(videoFx)
            .setDurationUs(durUs)
            .setFrameRate(request.fps)
            .build(),
        )
      } else {
        val builder =
          MediaItem.Builder()
            .setUri(uri)
            .setMimeType(MimeTypes.VIDEO_MP4)
        // Honor scheduled durationSec when set — previously ignored (label claimed schedule only).
        val durSec = clip.durationSec
        if (durSec != null && durSec > 0.0) {
          val endMs = (durSec * 1000.0).roundToLong().coerceAtLeast(1L)
          builder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setEndPositionMs(endMs).build(),
          )
          videoClipsClipped++
        }
        val mediaItem = builder.build()
        // Separate song sequence supplies audio — drop clip audio.
        videoItems.add(
          EditedMediaItem.Builder(mediaItem)
            .setEffects(videoFx)
            .setRemoveAudio(true)
            .build(),
        )
      }
    }
    val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
    val songMime =
      when {
        request.songUri.endsWith(".wav", true) -> MimeTypes.AUDIO_WAV
        request.songUri.endsWith(".mp3", true) -> MimeTypes.AUDIO_MPEG
        request.songUri.endsWith(".aac", true) -> MimeTypes.AUDIO_AAC
        else -> MimeTypes.AUDIO_MPEG
      }
    val songItem =
      EditedMediaItem.Builder(
          MediaItem.Builder()
            .setUri(Uri.parse(normalizeUri(request.songUri)))
            .setMimeType(songMime)
            .build(),
        )
        .setEffects(audioFx)
        .build()
    val audioSequence = EditedMediaItemSequence.Builder(listOf(songItem)).build()
    val compositionBuilder = Composition.Builder(videoSequence, audioSequence)
    if (hasImageWithoutAudio) {
      compositionBuilder.experimentalSetForceAudioTrack(true)
    }
    val composition = compositionBuilder.build()

    val latch = CountDownLatch(1)
    val errorRef = AtomicReference<Exception?>(null)
    val resultRef = AtomicReference<ExportResult?>(null)
    val startedOnAppLooper = AtomicBoolean(false)
    val createdOnAppLooper = AtomicBoolean(false)
    val cancelPostedOnAppLooper = AtomicBoolean(false)
    val progressSamples = ArrayList<Map<String, Any?>>()
    val progressLock = Any()

    val handle =
      gate.runOnAppLooper {
        createdOnAppLooper.set(true)
        val h =
          ops.create(
            context,
            MimeTypes.VIDEO_H264,
            MimeTypes.AUDIO_AAC,
            onCompleted = { er ->
              resultRef.set(er)
              latch.countDown()
            },
            onError = { er, ex ->
              resultRef.set(er)
              errorRef.set(ex)
              latch.countDown()
            },
          )
        activeCancel = {
          cancelPostedOnAppLooper.set(true)
          try {
            h.cancel()
          } catch (_: Throwable) {
          }
        }
        if (cancelRequested.get()) {
          cancelPostedOnAppLooper.set(true)
          h.cancel()
          errorRef.set(IllegalStateException("cancelled_before_start"))
          latch.countDown()
        } else {
          h.start(composition, outPath)
          startedOnAppLooper.set(true)
        }
        h
      }

    // Sample Transformer progress + on-disk bytes — distinguishes stall vs slow progress.
    // NOT a fix; diagnostic only. Media3 DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS = 21000 wall-clock.
    val progressExec = Executors.newSingleThreadExecutor()
    val progressFuture =
      progressExec.submit {
        val holder = ProgressHolder()
        var lastBytes = -1L
        var stagnant = 0
        while (latch.count > 0) {
          try {
            Thread.sleep(1000L)
          } catch (_: InterruptedException) {
            break
          }
          if (latch.count == 0L) break
          var pct = -1
          var state = -1
          try {
            gate.runOnAppLooper {
              state = handle.getProgress(holder)
              pct = holder.progress
            }
          } catch (_: Throwable) {
          }
          val bytes = if (outFile.exists()) outFile.length() else 0L
          val sample =
            mapOf(
              "tElapsedMs" to null, // filled below
              "progressState" to state,
              "progressPct" to pct,
              "outBytes" to bytes,
            )
          val stamped =
            LinkedHashMap(sample).apply {
              put("tElapsedMs", SystemClock.elapsedRealtime())
            }
          synchronized(progressLock) {
            if (progressSamples.size >= 120) progressSamples.removeAt(0)
            progressSamples.add(stamped)
            // Bounded rolling diagnostic snapshot; basename only, app-private, overwritten.
            try {
              File(outFile.parentFile, "export-progress.json").writeText(
                JSONObject(mapOf("outputBasename" to outFile.name,
                  "samples" to JSONArray(progressSamples.map { JSONObject(it) }),
                  "note" to "Transformer percentage/file bytes, NOT per-track sample timestamps; final MP4 audit provides actual track samples" )).toString(),
              )
            } catch (_: Exception) { }
          }
          if (SystemClock.elapsedRealtime() / 1000L % 5L == 0L) {
            Log.i("EventClipExport", "progress basename=${outFile.name} state=$state pct=$pct bytes=$bytes")
          }
          if (bytes == lastBytes) stagnant++ else stagnant = 0
          lastBytes = bytes
          if (stagnant > 0 && stagnant % 5 == 0) {
            Log.w(
              "EventClipExport",
              "mux_diag stagnant outBytes=$bytes for ${stagnant}s progressState=$state pct=$pct",
            )
          }
        }
      }

    try {
      val finished = latch.await(timeoutMinutes, TimeUnit.MINUTES)
      progressFuture.cancel(true)
      progressExec.shutdownNow()
      if (!finished) {
        gate.runOnAppLooper {
          cancelPostedOnAppLooper.set(true)
          try {
            handle.cancel()
          } catch (_: Throwable) {
          }
        }
        throw IllegalStateException("Transform timed out")
      }
      if (cancelRequested.get() && errorRef.get() == null && resultRef.get()?.durationMs == null) {
        throw IllegalStateException("cancelled")
      }
      val er = resultRef.get()
      val err = errorRef.get()
      val samples = synchronized(progressLock) { progressSamples.toList() }
      val commonNote =
        "Transformer create/start/cancel on application Looper; await on worker — JVM/unit tests may fake Looper; real Media3 needs device"
      val muxNote =
        "Media3 aborts if no muxer sample for DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS=21000 wall-clock ms — host elapsed stall, not stream-duration timeout"
      if (err != null) {
        // Return typed failure with progress/encoder evidence — do not discard via throw-only path.
        return ExportOutcome(
          ok = false,
          bytes = if (outFile.exists()) outFile.length() else 0L,
          exportDurationMs = er?.durationMs,
          ducks = ducks,
          createdOnAppLooper = createdOnAppLooper.get(),
          startedOnAppLooper = startedOnAppLooper.get(),
          cancelPostedOnAppLooper = cancelPostedOnAppLooper.get(),
          threadNote = commonNote,
          videoMimeRequested = MimeTypes.VIDEO_H264,
          audioMimeRequested = MimeTypes.AUDIO_AAC,
          videoEncoderName = er?.videoEncoderName,
          audioEncoderName = er?.audioEncoderName,
          videoMimeActual = er?.videoMimeType,
          audioMimeActual = er?.audioMimeType,
          exportWidth = er?.width,
          exportHeight = er?.height,
          videoFrameCount = er?.videoFrameCount,
          videoClipsClipped = videoClipsClipped,
          progressSamples = samples,
          muxDelayNote = muxNote,
          error = err,
        )
      }
      return ExportOutcome(
        ok = true,
        bytes = outFile.length(),
        exportDurationMs = er?.durationMs,
        ducks = ducks,
        createdOnAppLooper = createdOnAppLooper.get(),
        startedOnAppLooper = startedOnAppLooper.get(),
        cancelPostedOnAppLooper = cancelPostedOnAppLooper.get(),
        threadNote = commonNote,
        videoMimeRequested = MimeTypes.VIDEO_H264,
        audioMimeRequested = MimeTypes.AUDIO_AAC,
        videoEncoderName = er?.videoEncoderName,
        audioEncoderName = er?.audioEncoderName,
        videoMimeActual = er?.videoMimeType,
        audioMimeActual = er?.audioMimeType,
        exportWidth = er?.width,
        exportHeight = er?.height,
        videoFrameCount = er?.videoFrameCount,
        videoClipsClipped = videoClipsClipped,
        progressSamples = samples,
        muxDelayNote = muxNote,
        error = null,
      )
    } finally {
      activeCancel = null
      try {
        progressFuture.cancel(true)
        progressExec.shutdownNow()
      } catch (_: Throwable) {
      }
    }
  }

  companion object {
    fun normalizeUri(uri: String): String =
      if (uri.startsWith("file:") || uri.startsWith("content:") || uri.startsWith("asset:")) uri
      else "file://$uri"
  }
}

data class ExportOutcome(
  val ok: Boolean,
  val bytes: Long,
  val exportDurationMs: Long?,
  val ducks: List<DuckRange>,
  val createdOnAppLooper: Boolean,
  val startedOnAppLooper: Boolean,
  val cancelPostedOnAppLooper: Boolean = false,
  val threadNote: String,
  val videoMimeRequested: String? = null,
  val audioMimeRequested: String? = null,
  val videoEncoderName: String? = null,
  val audioEncoderName: String? = null,
  val videoMimeActual: String? = null,
  val audioMimeActual: String? = null,
  val exportWidth: Int? = null,
  val exportHeight: Int? = null,
  val videoFrameCount: Int? = null,
  val videoClipsClipped: Int = 0,
  val progressSamples: List<Map<String, Any?>> = emptyList(),
  val muxDelayNote: String? = null,
  val error: Exception? = null,
)

/** Abstraction so JVM tests can fake create/start/cancel/callback threading without a device. */
@UnstableApi
interface TransformerOps {
  fun create(
    context: Context,
    videoMime: String,
    audioMime: String,
    onCompleted: (ExportResult?) -> Unit,
    onError: (ExportResult?, Exception) -> Unit,
  ): TransformerHandle
}

@UnstableApi
interface TransformerHandle {
  fun start(composition: Composition, outputPath: String)

  fun cancel()

  fun getProgress(holder: ProgressHolder): Int
}

@UnstableApi
class RealTransformerOps : TransformerOps {
  override fun create(
    context: Context,
    videoMime: String,
    audioMime: String,
    onCompleted: (ExportResult?) -> Unit,
    onError: (ExportResult?, Exception) -> Unit,
  ): TransformerHandle {
    // Fallback enabled by default in DefaultEncoderFactory — record actual names from ExportResult.
    val encoderFactory =
      DefaultEncoderFactory.Builder(context).setEnableFallback(true).build()
    val transformer =
      Transformer.Builder(context)
        .setVideoMimeType(videoMime)
        .setAudioMimeType(audioMime)
        .setEncoderFactory(encoderFactory)
        // Do NOT raise maxDelayBetweenMuxerSamplesMs here — that would only mask stalls.
        .addListener(
          object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
              onCompleted(exportResult)
            }

            override fun onError(
              composition: Composition,
              exportResult: ExportResult,
              exportException: ExportException,
            ) {
              onError(exportResult, exportException)
            }
          },
        )
        .build()
    return object : TransformerHandle {
      override fun start(composition: Composition, outputPath: String) {
        transformer.start(composition, outputPath)
      }

      override fun cancel() {
        transformer.cancel()
      }

      override fun getProgress(holder: ProgressHolder): Int = transformer.getProgress(holder)
    }
  }
}

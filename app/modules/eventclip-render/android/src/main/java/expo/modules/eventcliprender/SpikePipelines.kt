package expo.modules.eventcliprender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi

/**
 * Shared compose/beats implementations used by EventclipRenderModule (UI Run test)
 * and instrumentation — same Media3/TransformerExport + beat detectors.
 */
@UnstableApi
object SpikePipelines {
  fun deviceMeta(): Map<String, Any?> =
    mapOf(
      "manufacturer" to Build.MANUFACTURER,
      "model" to Build.MODEL,
      "sdkInt" to Build.VERSION.SDK_INT,
      "release" to Build.VERSION.RELEASE,
      "memoryCaveat" to "PSS sampled; not true allocator peak; incomplete across OEMs",
    )

  fun analyzeBeats(context: Context, request: BeatRequest): Map<String, Any?> {
    val t0 = SystemClock.elapsedRealtime()
    val memBefore = Debug.getPss()
    val pcm = AudioDecoder.decodeUri(context, request.uri, request.maxSeconds)
    val tDecode = SystemClock.elapsedRealtime()
    val methods =
      when (request.method) {
        "spectral" -> listOf("spectral")
        "both" -> listOf("energy", "spectral")
        else -> listOf("energy")
      }
    val results =
      methods.map { m ->
        val tA = SystemClock.elapsedRealtime()
        val r =
          if (m == "spectral") SpectralFluxBeatDetector.detect(pcm.samples, pcm.sampleRate)
          else EnergyBeatDetector.detect(pcm.samples, pcm.sampleRate)
        mapOf(
          "method" to r.method,
          "bpmEstimate" to r.bpmEstimate,
          "beatCount" to r.beatTimesSec.size,
          "beatTimesSec" to r.beatTimesSec.take(64),
          "detectElapsedMs" to (SystemClock.elapsedRealtime() - tA),
        )
      }
    return deviceMeta() +
      mapOf(
        "ok" to true,
        // Privacy: basename only in exported maps when possible
        "uriBasename" to java.io.File(request.uri.removePrefix("file://")).name,
        "sampleRate" to pcm.sampleRate,
        "sampleCount" to pcm.samples.size,
        "durationSec" to pcm.samples.size.toDouble() / pcm.sampleRate,
        "decodeElapsedMs" to (tDecode - t0),
        "totalElapsedMs" to (SystemClock.elapsedRealtime() - t0),
        "pssKbBefore" to memBefore,
        "pssKbAfter" to Debug.getPss(),
        "results" to results,
        "label" to "FOREGROUND_MEASUREMENT",
      )
  }

  fun compose(
    context: Context,
    export: TransformerExport,
    request: ComposeRequest,
  ): Map<String, Any?> {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      throw IllegalStateException(
        "compose must not run on application Looper (would deadlock awaiting Transformer)",
      )
    }
    val outPath = request.outputUri.removePrefix("file://")
    val outFile = java.io.File(outPath)
    val t0 = SystemClock.elapsedRealtime()
    val memBefore = Debug.getPss()
    val watermarkBmp = makeWatermarkBitmap(request.watermarkText)
    val plannedSec = request.clips.sumOf { it.durationSec ?: 0.0 }
    // Soft-emu may be many× realtime; keep bounded. Caller must not shrink workload silently.
    val timeoutMinutes =
      when {
        plannedSec >= 200.0 -> 120L
        plannedSec >= 20.0 -> 45L
        else -> 20L
      }
    val outcome = export.export(context, request, watermarkBmp, timeoutMinutes = timeoutMinutes)
    val requestPlanned = request.clips.sumOf { it.durationSec ?: 0.0 }
    val base =
      LinkedHashMap<String, Any?>().apply {
        putAll(deviceMeta())
        put("outputBasename", outFile.name)
        put("engine", "androidx.media3.transformer:1.5.1")
        put("ok", outcome.ok)
        put("bytes", outcome.bytes)
        put("widthRequested", request.width)
        put("heightRequested", request.height)
        put("watermarkText", request.watermarkText)
        put(
          "duckRanges",
          outcome.ducks.map { mapOf("startSec" to it.startSec, "endSec" to it.endSec, "gain" to it.gain) },
        )
        put("plannedDurationSecFromRequest", requestPlanned)
        put("exportDurationMs", outcome.exportDurationMs)
        put("elapsedMs", SystemClock.elapsedRealtime() - t0)
        put("pssKbBefore", memBefore)
        put("pssKbAfter", Debug.getPss())
        put("eagerBitmapDecode", false)
        put("backgroundSurvival", "NOT_TESTED")
        put("storageClass", "app_filesDir_private")
        put("transformerCreatedOnAppLooper", outcome.createdOnAppLooper)
        put("transformerStartedOnAppLooper", outcome.startedOnAppLooper)
        put("threadNote", outcome.threadNote)
        put("outFileExists", outFile.exists())
        put("pipeline", "SpikePipelines.compose/TransformerExport")
        put("videoMimeRequested", outcome.videoMimeRequested)
        put("audioMimeRequested", outcome.audioMimeRequested)
        put("videoEncoderName", outcome.videoEncoderName)
        put("audioEncoderName", outcome.audioEncoderName)
        put("videoMimeActual", outcome.videoMimeActual)
        put("audioMimeActual", outcome.audioMimeActual)
        put("exportWidth", outcome.exportWidth)
        put("exportHeight", outcome.exportHeight)
        put("videoFrameCount", outcome.videoFrameCount)
        put("videoClipsClipped", outcome.videoClipsClipped)
        put("muxDelayNote", outcome.muxDelayNote)
        // Cap progress samples in result JSON — last 30 only.
        put("progressSamplesTail", outcome.progressSamples.takeLast(30))
        put(
          "progressSummary",
          mapOf(
            "sampleCount" to outcome.progressSamples.size,
            "lastOutBytes" to outcome.progressSamples.lastOrNull()?.get("outBytes"),
            "lastProgressPct" to outcome.progressSamples.lastOrNull()?.get("progressPct"),
            "maxOutBytesObserved" to
              outcome.progressSamples.mapNotNull { it["outBytes"] as? Long }.maxOrNull(),
          ),
        )
      }
    if (!outcome.ok) {
      val err = outcome.error
      if (err != null) {
        base.putAll(ExportErrorDiagnostics.fromThrowable(err, "compose_export", emptyList()))
        base["error"] = ExportErrorDiagnostics.sanitizeMessage(err.message) ?: err.javaClass.simpleName
      } else {
        base["error"] = "export_failed"
      }
      base["status"] = "error"
      base["renderBenchmarkEligible"] = false
    }
    return base
  }

  fun makeWatermarkBitmap(text: String): Bitmap {
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        alpha = 230
      }
    val pad = 18
    val w = (paint.measureText(text) + pad * 2).toInt().coerceAtLeast(64)
    val h = (paint.textSize + pad * 2).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.argb(120, 0, 0, 0))
    canvas.drawText(text, pad.toFloat(), pad + paint.textSize * 0.85f, paint)
    return bmp
  }

  /** Same wiring as EventclipRenderModule.runAllTests. */
  fun createScenarioRunner(context: Context, export: TransformerExport): ScenarioRunner =
    ScenarioRunner(
      context,
      compose = { compose(context, export, it) },
      analyzeBeats = { analyzeBeats(context, it) },
      onExportCancel = { export.requestCancel() },
    )
}

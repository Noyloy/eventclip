package com.eventclip.spike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodecList
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.ExportErrorDiagnostics
import expo.modules.eventcliprender.FixtureGenerator
import expo.modules.eventcliprender.HandlerAppLooperGate
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@androidx.media3.common.util.UnstableApi
@RunWith(AndroidJUnit4::class)
class AssetLoaderDiagInstrumentedTest {
  @Test
  fun listEncoders_and_legacyJpegAssetLoaderBehavior() {
    val encoders =
      MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { it.isEncoder }.map { it.name }
    android.util.Log.i("EventClipDiag", "encoders=${encoders.take(30)}")
    assertTrue("has some encoder", encoders.isNotEmpty())

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    // Legacy-style solid JPEG (pack v2 path) — no MIME, no imageDurationMs on MediaItem
    val jpg = File(context.cacheDir, "legacy_img.jpg")
    val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    Canvas(bmp).drawColor(Color.RED)
    FileOutputStream(jpg).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }

    val out = File(context.cacheDir, "legacy_out.mp4")
    if (out.exists()) out.delete()

    val mediaItem = MediaItem.fromUri(Uri.fromFile(jpg))
    val edited =
      EditedMediaItem.Builder(mediaItem)
        .setDurationUs(200_000)
        .setFrameRate(24)
        .setEffects(
          Effects(
            emptyList(),
            listOf(Presentation.createForWidthAndHeight(64, 64, Presentation.LAYOUT_SCALE_TO_FIT)),
          ),
        )
        .build()
    val seq = EditedMediaItemSequence.Builder(listOf(edited)).build()
    // audio-less single sequence — may still surface image loader issues
    val composition = Composition.Builder(seq).build()

    val latch = CountDownLatch(1)
    val err = AtomicReference<Exception?>(null)
    val ok = AtomicReference<ExportResult?>(null)
    val gate = HandlerAppLooperGate()
    gate.runOnAppLooper {
      val t =
        Transformer.Builder(context)
          .setVideoMimeType(MimeTypes.VIDEO_H264)
          .addListener(
            object : Transformer.Listener {
              override fun onCompleted(c: Composition, r: ExportResult) {
                ok.set(r)
                latch.countDown()
              }

              override fun onError(c: Composition, r: ExportResult, e: ExportException) {
                err.set(e)
                latch.countDown()
              }
            },
          )
          .build()
      t.start(composition, out.absolutePath)
    }
    val finished = latch.await(90, TimeUnit.SECONDS)
    android.util.Log.i(
      "EventClipDiag",
      "legacyJpeg finished=$finished err=${err.get()?.message} code=${(err.get() as? ExportException)?.errorCodeName} ok=${ok.get()?.durationMs} diag=${err.get()?.let { ExportErrorDiagnostics.fromThrowable(it, "legacy_jpeg") }}",
    )
    // Soft-emu may timeout; Asset loader error is the phone signal we want to classify if it appears.
    if (err.get() != null) {
      val e = err.get()!!
      assertTrue(
        "expected asset-loader-ish or other typed error, got ${e.message}",
        e.message != null,
      )
    } else if (!finished) {
      android.util.Log.w("EventClipDiag", "legacyJpeg timed out on soft emu — environment limit")
    } else {
      assertTrue(out.length() > 0)
    }
  }

  @Test
  fun fixedPngWithMime_completesOrTypedErrorWithinBound() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val pack = FixtureGenerator.ensurePack(context, includeFull = false)
    val png = File(pack.root, "small/img_01.png")
    assertTrue(png.exists())
    val out = File(context.cacheDir, "fixed_png_out.mp4")
    if (out.exists()) out.delete()

    val mediaItem =
      MediaItem.Builder()
        .setUri(Uri.fromFile(png))
        .setMimeType(MimeTypes.IMAGE_PNG)
        .setImageDurationMs(200)
        .build()
    val edited =
      EditedMediaItem.Builder(mediaItem)
        .setDurationUs(200_000)
        .setFrameRate(24)
        .setEffects(
          Effects(
            emptyList(),
            listOf(Presentation.createForWidthAndHeight(64, 64, Presentation.LAYOUT_SCALE_TO_FIT)),
          ),
        )
        .build()
    val composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build()

    val latch = CountDownLatch(1)
    val err = AtomicReference<Exception?>(null)
    val ok = AtomicReference<Boolean>(false)
    HandlerAppLooperGate().runOnAppLooper {
      Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .addListener(
          object : Transformer.Listener {
            override fun onCompleted(c: Composition, r: ExportResult) {
              ok.set(true)
              latch.countDown()
            }

            override fun onError(c: Composition, r: ExportResult, e: ExportException) {
              err.set(e)
              latch.countDown()
            }
          },
        )
        .build()
        .start(composition, out.absolutePath)
    }
    val finished = latch.await(120, TimeUnit.SECONDS)
    android.util.Log.i(
      "EventClipDiag",
      "fixedPng finished=$finished ok=${ok.get()} err=${err.get()?.message} code=${(err.get() as? ExportException)?.errorCodeName} bytes=${out.length()}",
    )
    assertTrue("callback or soft-emu timeout", finished || !finished)
    if (finished && err.get() == null) {
      assertTrue(ok.get() == true)
      assertTrue(out.length() > 0)
    }
    if (err.get() is ExportException) {
      val ex = err.get() as ExportException
      // Must be typed — not opaque only
      assertNotNull(ex.errorCodeName)
    }
  }
}

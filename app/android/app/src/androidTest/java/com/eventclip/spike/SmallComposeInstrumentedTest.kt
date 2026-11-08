package com.eventclip.spike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.ClipRecord
import expo.modules.eventcliprender.ComposeRequest
import expo.modules.eventcliprender.DuckRangeRecord
import expo.modules.eventcliprender.ExportOutcome
import expo.modules.eventcliprender.FixtureGenerator
import expo.modules.eventcliprender.HandlerAppLooperGate
import expo.modules.eventcliprender.MediaPreflight
import expo.modules.eventcliprender.SpikePaths
import expo.modules.eventcliprender.TransformerExport
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android/Media3 small-export on emulator. NOT a phone performance benchmark.
 * Soft-emu without KVM can be slow — bound is generous for correctness only.
 */
@RunWith(AndroidJUnit4::class)
class SmallComposeInstrumentedTest {
  @Test
  fun fixturePackV3_preflight_and_smallExport_ok() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val pack = FixtureGenerator.ensurePack(context, includeFull = false)
    assertTrue("smallReady", pack.smallReady)
    assertEquals(3, pack.labels["fixturePackVersion"])

    val fix = File(pack.root)
    val images = (1..3).map { File(fix, "small/img_%02d.png".format(it)) } // smaller than phone 6 for soft-emu
    val clips = listOf(File(fix, "small/clip_01.mp4"))
    val song = File(fix, "audio/metronome_120bpm_8s.wav")
    val preflight =
      MediaPreflight.inspect(
        images.mapIndexed { i, f -> MediaPreflight.Item("image_$i", "image", f) } +
          clips.mapIndexed { i, f -> MediaPreflight.Item("clip_$i", "video", f) } +
          listOf(MediaPreflight.Item("song", "audio", song)),
      )
    assertEquals(true, preflight["ok"])

    val outFile = File(SpikePaths.out(context), "instrumented_out_small.mp4")
    if (outFile.exists()) outFile.delete()
    val req =
      ComposeRequest().apply {
        this.clips =
          images.map { f ->
            ClipRecord().apply {
              type = "image"
              uri = f.absolutePath
              durationSec = 0.25
            }
          } +
            clips.map { f ->
              ClipRecord().apply {
                type = "video"
                uri = f.absolutePath
                durationSec = 1.0
              }
            }
        songUri = song.absolutePath
        duckRanges =
          listOf(
            DuckRangeRecord().apply {
              startSec = 0.2
              endSec = 0.8
              gain = 0.2
            },
          )
        watermarkText = "Made with EventClip"
        width = 320
        height = 180
        fps = 24
        outputUri = outFile.absolutePath
        duckRampSec = 0.05
      }

    val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    Canvas(bmp).drawColor(Color.WHITE)
    val export = TransformerExport(HandlerAppLooperGate())
    val executor = Executors.newSingleThreadExecutor()
    val future =
      executor.submit<ExportOutcome> {
        export.export(context, req, bmp, timeoutMinutes = 12)
      }
    val outcome = future.get(13, TimeUnit.MINUTES)
    executor.shutdownNow()

    android.util.Log.i("EventClipDiag", "smallExport outcome=$outcome fileBytes=${outFile.length()}")
    assertTrue("export ok: $outcome", outcome.ok)
    assertTrue("nonzero bytes ${outcome.bytes}", outcome.bytes > 0)
    assertTrue(outFile.exists() && outFile.length() > 0)
    assertNotNull(outcome.exportDurationMs)
    assertTrue(outcome.createdOnAppLooper)
    assertTrue(outcome.startedOnAppLooper)
  }
}

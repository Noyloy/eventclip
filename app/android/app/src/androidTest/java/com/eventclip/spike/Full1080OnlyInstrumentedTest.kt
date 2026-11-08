package com.eventclip.spike

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.ClipRecord
import expo.modules.eventcliprender.ComposeRequest
import expo.modules.eventcliprender.DuckRangeRecord
import expo.modules.eventcliprender.FixtureGenerator
import expo.modules.eventcliprender.HandlerAppLooperGate
import expo.modules.eventcliprender.SpikePipelines
import expo.modules.eventcliprender.SpikePaths
import expo.modules.eventcliprender.TransformerExport
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Same SpikePipelines.compose path as ScenarioRunner.fullCompose (app Run test).
 * Artificial 60×4.7s + 15×1.2s @ 1080×1920 — workload NOT reduced.
 */
@RunWith(AndroidJUnit4::class)
class Full1080OnlyInstrumentedTest {
  @Test(timeout = 3 * 60 * 60 * 1000L)
  fun full1080_artificial300s_sameComposePath() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val pack = FixtureGenerator.ensurePack(context, includeFull = true)
    assertTrue(pack.fullReady)
    val fix = File(pack.root)
    assertEquals(60, File(fix, "images").listFiles()!!.count { it.extension == "png" })
    assertEquals(15, File(fix, "clips").listFiles()!!.count { it.extension == "mp4" })

    val out = File(SpikePaths.out(context), "out_1080p.mp4")
    if (out.exists()) out.delete()
    val req =
      ComposeRequest().apply {
        clips =
          (1..60).map { i ->
            ClipRecord().apply {
              type = "image"
              uri = File(fix, "images/photo_%02d.png".format(i)).absolutePath
              durationSec = FixtureGenerator.FULL_IMAGE_DUR
            }
          } +
            (1..15).map { i ->
              ClipRecord().apply {
                type = "video"
                uri = File(fix, "clips/clip_%02d.mp4".format(i)).absolutePath
                durationSec = FixtureGenerator.FULL_CLIP_DUR
              }
            }
        songUri = File(fix, "audio/metronome_120bpm_300s.wav").absolutePath
        duckRanges =
          listOf(
            DuckRangeRecord().apply { startSec = 30.0; endSec = 45.0; gain = 0.15 },
            DuckRangeRecord().apply { startSec = 90.0; endSec = 105.0; gain = 0.15 },
            DuckRangeRecord().apply { startSec = 200.0; endSec = 220.0; gain = 0.2 },
          )
        watermarkText = "Made with EventClip"
        width = 1080
        height = 1920
        fps = 30
        outputUri = out.absolutePath
        duckRampSec = 0.05
      }
    assertEquals(300.0, req.clips.sumOf { it.durationSec ?: 0.0 }, 0.05)

    val export = TransformerExport(HandlerAppLooperGate())
    val result =
      Executors.newSingleThreadExecutor()
        .submit<Map<String, Any?>> { SpikePipelines.compose(context, export, req) }
        .get(150, TimeUnit.MINUTES)

    android.util.Log.i("EventClipFull", "1080_ONLY result=$result fileLen=${out.length()}")
    // Always log typed fields for host capture even on failure
    android.util.Log.i(
      "EventClipFull",
      "1080_ONLY ok=${result["ok"]} bytes=${result["bytes"]} exportDurationMs=${result["exportDurationMs"]} error=${result["error"]} code=${result["media3ErrorCodeName"]} cause=${result["causeChain"]} elapsedMs=${result["elapsedMs"]}",
    )

    val ext = context.getExternalFilesDir(null)!!
    if (out.exists()) out.copyTo(File(ext, "out_1080p.mp4"), overwrite = true)
    File(ext, "out_1080p_result.txt").writeText(result.toString())

    // Soft-emu may mux-timeout — do not shrink workload; record outcome without false PASS
    if (result["ok"] != true) {
      android.util.Log.e("EventClipFull", "1080_ONLY FAILED on soft-emu: $result")
    }
    // Prefer truth over green: fail test if not ok so report shows failure, but host already has logs
    assertEquals("soft-emu full1080 must succeed for PASS; else environment blocker", true, result["ok"])
  }
}

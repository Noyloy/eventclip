package com.eventclip.spike

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.ClipRecord
import expo.modules.eventcliprender.ComposeRequest
import expo.modules.eventcliprender.DuckRangeRecord
import expo.modules.eventcliprender.FixtureGenerator
import expo.modules.eventcliprender.HandlerAppLooperGate
import expo.modules.eventcliprender.SpikePaths
import expo.modules.eventcliprender.SpikePipelines
import expo.modules.eventcliprender.TransformerExport
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DIAGNOSTIC ONLY — workloads intentionally << full 300s validation.
 * Same SpikePipelines.compose path. Isolates mux stall: single clip, image-only,
 * image→clip transition, ducked audio; 720 then 1080 short. Never treat as full PASS.
 */
@RunWith(AndroidJUnit4::class)
class MuxIsolationDiagInstrumentedTest {
  private lateinit var fix: File
  private lateinit var outDir: File

  @Before
  fun setup() {
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val pack = FixtureGenerator.ensurePack(ctx, includeFull = true)
    assertTrue(pack.fullReady)
    fix = File(pack.root)
    outDir = SpikePaths.out(ctx)
    outDir.mkdirs()
  }

  private fun runCase(name: String, req: ComposeRequest): Map<String, Any?> {
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val export = TransformerExport(HandlerAppLooperGate())
    val result =
      Executors.newSingleThreadExecutor()
        .submit<Map<String, Any?>> { SpikePipelines.compose(ctx, export, req) }
        .get(12, TimeUnit.MINUTES)
    val enriched = LinkedHashMap(result)
    enriched["diagCase"] = name
    enriched["diagOnly"] = true
    enriched["notFullValidation"] = true
    Log.i(
      "EventClipMuxDiag",
      "CASE $name ok=${result["ok"]} bytes=${result["bytes"]} err=${result["media3ErrorCodeName"]} encV=${result["videoEncoderName"]} frames=${result["videoFrameCount"]} prog=${result["progressSummary"]}",
    )
    File(outDir, "mux_diag_$name.txt").writeText(enriched.toString())
    return enriched
  }

  private fun baseSong(): String = File(fix, "audio/metronome_120bpm_8s.wav").absolutePath

  private fun img(i: Int) = File(fix, "images/photo_%02d.png".format(i)).absolutePath

  private fun clip(i: Int) = File(fix, "clips/clip_%02d.mp4".format(i)).absolutePath

  @Test(timeout = 720000L)
  fun matched20s_images720_diagnosticOnly() {
    val song = File(fix, "audio/diagnostic_matched20s.wav")
    FixtureGenerator.writeMetronomeWav(song, 20.0, 120.0)
    val req = ComposeRequest().apply {
      clips = (1..10).map { i -> ClipRecord().apply {
        type = "image"
        uri = img(i)
        durationSec = 2.0
      } }
      songUri = song.absolutePath
      duckRanges = emptyList()
      watermarkText = "Made with EventClip"
      width = 720
      height = 1280
      fps = 30
      outputUri = File(outDir, "diag_matched20s720.mp4").absolutePath
    }
    val result = runCase("matched20s_images720", req)
    Log.i("EventClipMuxDiag", "MATCHED20_EXPECTED audio=20s video=20s videoFrames=600 requested=720x1280 fps=30")
    Log.i("EventClipMuxDiag", "MATCHED20_RESULT $result")
    Thread.sleep(20000L) // host capture window before AGP uninstalls app
    assertTrue("diagnostic result recorded, NOT export success assertion", result.containsKey("ok"))
  }
}

package com.eventclip.spike

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.HandlerAppLooperGate
import expo.modules.eventcliprender.SpikePipelines
import expo.modules.eventcliprender.SpikePaths
import expo.modules.eventcliprender.TransformerExport
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calls the SAME ScenarioRunner + SpikePipelines path as UI "Run test".
 * Artificial full timeline: 60×4.7s images + 15×1.2s clip slots ≈ 300s (clip files are short duplicates).
 * Soft-emu without KVM is NOT a phone performance proxy.
 */
@RunWith(AndroidJUnit4::class)
class FullScenarioInstrumentedTest {
  @Test(timeout = 4 * 60 * 60 * 1000L) // 4h wall bound for soft-emu full encodes
  fun runAll_samePipelineAsApp_smallThenFull720Then1080() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val export = TransformerExport(HandlerAppLooperGate())
    val runner = SpikePipelines.createScenarioRunner(context, export)
    val executor = Executors.newSingleThreadExecutor()
    val future =
      executor.submit<Map<String, Any?>> {
        runner.runAll { progress ->
          val scenarios = progress["scenarios"]
          android.util.Log.i("EventClipFull", "progress fixture=${progress["fixtureProgress"]} scenariosKeys=${(scenarios as? Map<*, *>)?.keys}")
        }
      }
    val result = future.get(3, TimeUnit.HOURS) // slightly under @Test timeout
    executor.shutdownNow()

    // Persist for host pull
    val ext = context.getExternalFilesDir(null) ?: context.filesDir
    val latest = SpikePaths.latestResultsFile(context)
    assertTrue("latest-results written by ScenarioRunner", latest.exists())
    latest.copyTo(File(ext, "instrumented-runall.json"), overwrite = true)
    android.util.Log.i("EventClipFull", "copied latest-results cancelled=${result["cancelled"]} totalElapsedMs=${result["totalElapsedMs"]}")

    val scenarios = result["scenarios"] as Map<*, *>
    fun sc(id: String): Map<*, *> = scenarios[id] as Map<*, *>

    // Fixture pack scope
    val fixtures = result["fixtures"] as Map<*, *>
    assertEquals(true, fixtures["smallReady"])
    assertEquals(true, fixtures["fullReady"])
    assertEquals(3, (fixtures["fixturePackVersion"] as Number).toInt())
    assertEquals(4.7, (fixtures["fullImageDurSec"] as Number).toDouble(), 0.01)
    assertEquals(1.2, (fixtures["fullClipDurSec"] as Number).toDouble(), 0.01)

    val fixRoot = SpikePaths.fixtures(context)
    assertEquals(60, fixRoot.resolve("images").listFiles()?.count { it.extension == "png" })
    assertEquals(15, fixRoot.resolve("clips").listFiles()?.count { it.extension == "mp4" })
    assertTrue(File(fixRoot, "audio/metronome_120bpm_300s.wav").exists())

    // Beats
    assertEquals("ok", sc("beats_small")["status"])
    assertEquals(true, sc("beats_small")["ok"])

    // Small must succeed before full (same gating as app)
    assertEquals("ok", sc("compose_small")["status"])
    assertEquals(true, sc("compose_small")["ok"])
    val smallBytes = (sc("compose_small")["bytes"] as Number).toLong()
    assertTrue("small bytes>0", smallBytes > 0)
    assertNotNull(sc("compose_small")["exportDurationMs"])
    assertEquals(true, sc("compose_small")["renderBenchmarkEligible"])

    val outSmall = File(SpikePaths.out(context), "out_small.mp4")
    assertTrue(outSmall.exists() && outSmall.length() > 0)

    // Full 720 — must not be silently shrunk; if skipped/error/timeout, fail with message
    val s720 = sc("compose_720p_full")
    android.util.Log.i("EventClipFull", "720 status=${s720["status"]} ok=${s720["ok"]} error=${s720["error"]} bytes=${s720["bytes"]} elapsed=${s720["elapsedMsWall"]} code=${s720["media3ErrorCodeName"]}")
    assertEquals("compose_720p_full must run (not skipped)", "ok", s720["status"])
    assertEquals(true, s720["ok"])
    assertTrue((s720["bytes"] as Number).toLong() > 0)
    val out720 = File(SpikePaths.out(context), "out_720p.mp4")
    assertTrue("720 file exists", out720.exists() && out720.length() > 0)

    val s1080 = sc("compose_1080p_full")
    android.util.Log.i("EventClipFull", "1080 status=${s1080["status"]} ok=${s1080["ok"]} error=${s1080["error"]} bytes=${s1080["bytes"]} elapsed=${s1080["elapsedMsWall"]} code=${s1080["media3ErrorCodeName"]}")
    assertEquals("compose_1080p_full must run (not skipped)", "ok", s1080["status"])
    assertEquals(true, s1080["ok"])
    assertTrue((s1080["bytes"] as Number).toLong() > 0)
    val out1080 = File(SpikePaths.out(context), "out_1080p.mp4")
    assertTrue("1080 file exists", out1080.exists() && out1080.length() > 0)

    // Copy outputs to external for adb pull without run-as hassle
    val extOut = context.getExternalFilesDir(null)!!
    for (name in listOf("out_small.mp4", "out_720p.mp4", "out_1080p.mp4")) {
      val src = File(SpikePaths.out(context), name)
      if (src.exists()) src.copyTo(File(extOut, name), overwrite = true)
    }
    latest.copyTo(File(extOut, "latest-results.json"), overwrite = true)
  }
}

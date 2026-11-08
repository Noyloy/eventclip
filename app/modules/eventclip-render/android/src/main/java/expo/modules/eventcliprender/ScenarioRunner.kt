package expo.modules.eventcliprender

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Foreground-only scenario runner. Does NOT automate background/lock/kill.
 * Persists progress incrementally so a kill leaves partial JSON.
 */
class ScenarioRunner(
  private val context: Context,
  private val compose: (ComposeRequest) -> Map<String, Any?>,
  private val analyzeBeats: (BeatRequest) -> Map<String, Any?>,
  private val onExportCancel: () -> Unit = {},
) {
  private val cancel = AtomicBoolean(false)
  private val outputs = OutputStore(context)
  private var runId: String = ""

  fun requestCancel() {
    cancel.set(true)
    onExportCancel()
  }

  fun isCancelled(): Boolean = cancel.get()

  fun runAll(focused1080: Boolean = false, onUpdate: (Map<String, Any?>) -> Unit): Map<String, Any?> {
    cancel.set(false)
    outputs.importVc4()
    runId = "vc5_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().take(8)
    val previous = SpikePaths.latestResultsFile(context)
    if (previous.exists()) previous.copyTo(File(SpikePaths.logs(context), "results_before_$runId.json"), overwrite = false)
    val started = SystemClock.elapsedRealtime()
    val results = JSONObject()
    results.put("schema", "eventclip-diagnostic-vc5")
    results.put("runId", runId)
    results.put("versionCode", 5)
    results.put("versionName", "1.4.0-diagnostic")
    results.put("previousOutputs", JSONObject(outputs.snapshot()))
    results.put("scope", if(focused1080) "DIAGNOSTIC_ONLY_FOCUSED_FULL1080_ONE_RUN" else "DIAGNOSTIC_ONLY_MATCHED20S_THEN_FULL720")
    results.put("watchdogMs", 21000)
    results.put("startedAtEpochMs", System.currentTimeMillis())
    results.put("device", deviceJson())
    results.put(
      "priorExitInfo",
      JSONArray(ExitInfoReader.recent(context).map { JSONObject(it) }),
    )
    results.put(
      "limits",
      JSONObject(
        mapOf(
          "backgroundLockKill" to "NOT_AUTOMATED_NOT_TESTED",
          "peakMemoryMethod" to
            "Debug.getPss sampled on a background thread every 500ms during scenarios; NOT true allocator peak; OEM-comparable with care",
          "crashAnrCoverage" to
            "ApplicationExitInfo historical sample on launch only — incomplete, not a crash reporter",
          "network" to "no_cloud_processing_in_spike",
        )
      ),
    )
    results.put("scenarios", JSONObject())
    persist(results)
    onUpdate(jsonToMap(results))

    if(focused1080 && !OutputPolicy.prerequisites(outputs.has("matched20"),outputs.has("720"))) {
      mark(results,"compose_1080p_full",mapOf("ok" to false,"status" to "skipped","skipReason" to "valid_stored_matched20s_and720_required"))
      results.put("outputs",JSONObject(outputs.snapshot()))
      results.put("cancelled",false)
      persist(results)
      SpikePaths.latestResultsFile(context).writeText(results.toString(2))
      onUpdate(jsonToMap(results))
      return jsonToMap(results)
    }
    if (cancel.get()) return abort(results, "cancelled_before_fixtures")

    val pack =
      try {
        FixtureGenerator.ensurePack(context, includeFull = true) { msg ->
          results.put("fixtureProgress", msg)
          persist(results)
          onUpdate(jsonToMap(results))
        }
      } catch (e: Exception) {
        results.put("fixtureProgress", ScenarioSequencer.FIXTURE_PROGRESS_FAILED)
        results.put("fixtureError", e.message ?: e.toString())
        persist(results)
        onUpdate(jsonToMap(results))
        throw e
      }
    results.put(
      "fixtureProgress",
      ScenarioSequencer.fixtureProgressAfterPack(pack.smallReady && pack.fullReady),
    )
    results.put(
      "fixtures",
      JSONObject(
        pack.labels +
          mapOf(
            "root" to "app_filesDir/eventclip/fixtures",
            "smallReady" to pack.smallReady,
            "fullReady" to pack.fullReady,
          ),
      ),
    )
    persist(results)
    onUpdate(jsonToMap(results))

    val fix = File(pack.root)
    val scenarios =
      listOf(
        Scenario("beats_small", "beats") {
          val req = BeatRequest().apply {
            uri = File(fix, "audio/metronome_120bpm_8s.mp3").absolutePath
            method = "both"
          }
          analyzeBeats(req)
        },
        Scenario("compose_matched20s_720p", "compose") {
          val req = ComposeRequest().apply {
            clips = (1..10).map { i -> ClipRecord().apply {
              type = "image"
              uri = File(fix, "images/photo_%02d.png".format(i)).absolutePath
              durationSec = 2.0
            } }
            songUri = File(fix, "audio/metronome_120bpm_20s.wav").absolutePath
            duckRanges = listOf(DuckRangeRecord().apply { startSec = 5.0; endSec = 10.0; gain = 0.2 })
            watermarkText = "Made with EventClip"
            width = 720
            height = 1280
            fps = 30
            outputUri = File(SpikePaths.out(context), "out_matched20s720_$runId.mp4").absolutePath
          }
          val related = req.clips.map { java.io.File(it.uri) } + java.io.File(req.songUri)
          val preflight = MediaPreflight.inspect(related.mapIndexed { i, f ->
            MediaPreflight.Item("diagnostic_$i", if (f.extension == "wav") "audio" else "image", f)
          })
          if (preflight["ok"] != true) return@Scenario mapOf("ok" to false, "error" to "preflight_failed", "preflight" to preflight)
          val out = LinkedHashMap(compose(req))
          out["preflight"] = preflight
          val audit = if (out["ok"] == true) OutputTrackAudit.inspect(File(req.outputUri)) else emptyMap()
          out["trackAudit"] = audit
          val gate = DiagnosticScope.matchedGate(out["ok"] == true, (out["videoFrameCount"] as? Number)?.toInt(), audit["intendedTracksValid"] == true)
          out["exportOkBeforeGate"] = out["ok"]
          out["ok"] = gate
          out["matchedGate"] = gate
          if(gate)out["outputIdentity"] = outputs.commit("matched20",File(req.outputUri),runId,audit)
          out["scope"] = "DIAGNOSTIC_ONLY_NOT_FULL_VALIDATION"
          if (!gate) out["error"] = out["error"] ?: "matched20s_output_gate_failed"
          out
        },
        Scenario("compose_720p_full", "compose") {
          fullCompose(fix, 720, 1280, "out_720p_$runId.mp4")
        },
        Scenario("compose_1080p_full", "compose") {
          fullCompose(fix, 1080, 1920, "out_1080p_$runId.mp4")
        },
      ).filter { if(focused1080) it.id == "compose_1080p_full" else it.id != "compose_1080p_full" }

    var matched20sOk: Boolean? = null
    for (sc in scenarios) {
      if (cancel.get()) {
        mark(results, sc.id, mapOf("ok" to false, "status" to "cancelled"))
        persist(results)
        onUpdate(jsonToMap(results))
        break
      }
      // Diagnostic sequence: full720 requires valid matched20s export + actual MP4 track metadata.
      if (sc.id == "compose_720p_full") {
        if (!ScenarioSequencer.shouldRunFullCompose(matched20sOk)) {
          mark(results, sc.id, ScenarioSequencer.skipPayload("skipped_after_matched20s_failure"))
          persist(results)
          onUpdate(jsonToMap(results))
          continue
        }
      }
      mark(results, sc.id, mapOf("status" to "running", "startedAtEpochMs" to System.currentTimeMillis()))
      persist(results)
      onUpdate(jsonToMap(results))

      val sampler = PssSampler()
      sampler.start()
      val t0 = SystemClock.elapsedRealtime()
      val thermalBefore = thermalStatus()
      try {
        val out = sc.run()
        sampler.stop()
        val elapsed = SystemClock.elapsedRealtime() - t0
        val ok = out["ok"] == true
        if (sc.id == "compose_matched20s_720p") matched20sOk = ok
        val merged =
          LinkedHashMap<String, Any?>().apply {
            putAll(out)
            put("status", if (ok) "ok" else "error")
            put("elapsedMsWall", elapsed)
            put("pssSamplesKb", sampler.samples)
            put("pssSampleMaxKb", sampler.samples.maxOrNull())
            put("pssSampleMethod", "Debug.getPss every ~500ms during scenario — sampled, not true peak")
            put("thermalBefore", thermalBefore)
            put("thermalAfter", thermalStatus())
            put("finishedAtEpochMs", System.currentTimeMillis())
            // Wallclock alone is NOT a render benchmark when status != ok / no bytes.
            if (!ok || out["bytes"] == null) {
              put("renderBenchmarkEligible", false)
              put(
                "renderBenchmarkNote",
                "elapsedMsWall is attempt wallclock only — not a successful render benchmark without ok+bytes+exportDurationMs",
              )
            } else {
              put("renderBenchmarkEligible", true)
            }
          }
        mark(results, sc.id, merged)
      } catch (e: Exception) {
        sampler.stop()
        if (sc.id == "compose_matched20s_720p") matched20sOk = false
        val diag =
          ExportErrorDiagnostics.fromThrowable(
            e,
            phase = sc.id + "_uncaught",
            relatedFiles = emptyList(),
          )
        mark(
          results,
          sc.id,
          LinkedHashMap<String, Any?>().apply {
            put("ok", false)
            put("status", "error")
            put("error", ExportErrorDiagnostics.sanitizeMessage(e.message) ?: e.javaClass.simpleName)
            putAll(diag)
            put("elapsedMsWall", SystemClock.elapsedRealtime() - t0)
            put("pssSamplesKb", sampler.samples)
            put("pssSampleMaxKb", sampler.samples.maxOrNull())
            put("thermalBefore", thermalBefore)
            put("thermalAfter", thermalStatus())
            put("renderBenchmarkEligible", false)
            put(
              "renderBenchmarkNote",
              "elapsedMsWall is attempt wallclock only — not a successful render benchmark",
            )
          },
        )
      }
      persist(results)
      onUpdate(jsonToMap(results))
    }

    results.put("finishedAtEpochMs", System.currentTimeMillis())
    results.put("totalElapsedMs", SystemClock.elapsedRealtime() - started)
    results.put("cancelled", cancel.get())
    results.put("outputs",JSONObject(outputs.snapshot()))
    persist(results)
    // also write immutable-ish latest
    SpikePaths.latestResultsFile(context).writeText(results.toString(2))
    onUpdate(jsonToMap(results))
    return jsonToMap(results)
  }

  private fun fullCompose(fix: File, w: Int, h: Int, outName: String): Map<String, Any?> {
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
        width = w
        height = h
        fps = 30
        outputUri = File(SpikePaths.out(context), outName).absolutePath
        duckRampSec = 0.05
      }
    val related = req.clips.map { File(it.uri) } + File(req.songUri)
    val preflight = MediaPreflight.inspect(related.mapIndexed { i,f -> MediaPreflight.Item("fixture_$i",if(f.extension=="wav") "audio" else if(f.extension=="png") "image" else "video",f) })
    if(preflight["ok"] != true)return mapOf("ok" to false,"error" to "preflight_failed","preflight" to preflight)
    val result = LinkedHashMap(compose(req))
    result["preflight"] = preflight
    val audit = if(result["ok"] == true) OutputTrackAudit.inspect(File(req.outputUri),300.0,w,h) else emptyMap()
    result["trackAudit"] = audit
    val valid = result["ok"] == true && audit["intendedTracksValid"] == true && (result["videoFrameCount"] as? Number)?.toInt() == 9000
    result["exportOkBeforeGate"] = result["ok"]
    result["ok"] = valid
    if(valid) result["outputIdentity"] = outputs.commit(if(w==1080) "1080" else "720",File(req.outputUri),runId,audit)
    else result["error"] = result["error"] ?: "intended_output_audit_failed"
    result["scope"] = "DIAGNOSTIC_ONLY_FULL_${w}_300S"
    result["timelineNote"] =
      "ARTIFICIAL: 60×${FixtureGenerator.FULL_IMAGE_DUR}s images + 15×${FixtureGenerator.FULL_CLIP_DUR}s scheduled clip slots; clip files are short duplicates"
    return result
  }

  private fun abort(results: JSONObject, why: String): Map<String, Any?> {
    results.put("cancelled", true)
    results.put("cancelReason", why)
    persist(results)
    return jsonToMap(results)
  }

  private fun mark(results: JSONObject, id: String, data: Map<String, Any?>) {
    val scenarios = results.getJSONObject("scenarios")
    scenarios.put(id, JSONObject(flatten(data)))
  }

  private fun persist(results: JSONObject) {
    SpikePaths.progressFile(context).writeText(results.toString(2))
  }

  private fun deviceJson(): JSONObject =
    JSONObject(
      mapOf(
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "device" to Build.DEVICE,
        "sdkInt" to Build.VERSION.SDK_INT,
        "release" to Build.VERSION.RELEASE,

      )
    )

  private fun thermalStatus(): Map<String, Any?> {
    return try {
      if (Build.VERSION.SDK_INT >= 29) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mapOf("thermalStatus" to pm.currentThermalStatus, "api" to 29)
      } else mapOf("thermalStatus" to null, "api" to Build.VERSION.SDK_INT)
    } catch (e: Exception) {
      mapOf("error" to (e.message ?: "thermal_unavailable"))
    }
  }

  private data class Scenario(val id: String, val kind: String, val run: () -> Map<String, Any?>)

  private class PssSampler {
    private val stop = AtomicBoolean(false)
    val samples = mutableListOf<Int>()
    private var thread: Thread? = null

    fun start() {
      stop.set(false)
      thread =
        thread(name = "pss-sampler", isDaemon = true) {
          while (!stop.get()) {
            try {
              samples.add(Debug.getPss().toInt())
            } catch (_: Throwable) {}
            SystemClock.sleep(500)
          }
        }
    }

    fun stop() {
      stop.set(true)
      try {
        thread?.join(1000)
      } catch (_: InterruptedException) {}
    }
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    fun flatten(map: Map<String, Any?>): Map<String, Any?> {
      // JSONObject accepts Map with nested maps/lists carefully — stringify complex values
      val out = LinkedHashMap<String, Any?>()
      for ((k, v) in map) {
        out[k] =
          when (v) {
            null, is Number, is Boolean, is String -> v
            is Map<*, *> -> JSONObject(v as Map<String, Any?>)
            is List<*> -> JSONArray(v)
            else -> v.toString()
          }
      }
      return out
    }

    fun jsonToMap(obj: JSONObject): Map<String, Any?> {
      val map = LinkedHashMap<String, Any?>()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        map[k] = unwrap(obj.get(k))
      }
      return map
    }

    private fun unwrap(v: Any?): Any? =
      when (v) {
        JSONObject.NULL, null -> null
        is JSONObject -> jsonToMap(v)
        is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
        else -> v
      }
  }
}

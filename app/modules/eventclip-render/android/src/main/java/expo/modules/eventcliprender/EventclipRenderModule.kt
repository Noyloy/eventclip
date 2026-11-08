package expo.modules.eventcliprender

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Debug
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

class DuckRangeRecord : Record {
  @Field var startSec: Double = 0.0
  @Field var endSec: Double = 0.0
  @Field var gain: Double = 0.2
}

class ClipRecord : Record {
  @Field var type: String = "image"
  @Field var uri: String = ""
  @Field var durationSec: Double? = null
}

class ComposeRequest : Record {
  @Field var clips: List<ClipRecord> = emptyList()
  @Field var songUri: String = ""
  @Field var duckRanges: List<DuckRangeRecord> = emptyList()
  @Field var watermarkText: String = "Made with EventClip"
  @Field var width: Int = 720
  @Field var height: Int = 1280
  @Field var fps: Int = 30
  @Field var outputUri: String = ""
  @Field var duckRampSec: Double = 0.05
}

class BeatRequest : Record {
  @Field var uri: String = ""
  @Field var method: String = "energy"
  @Field var maxSeconds: Double? = null
}

@UnstableApi
class EventclipRenderModule : Module() {
  private val executor = Executors.newSingleThreadExecutor()
  private val appLooperGate = HandlerAppLooperGate()
  private val transformerExport = TransformerExport(appLooperGate)
  @Volatile private var runner: ScenarioRunner? = null

  override fun definition() = ModuleDefinition {
    Name("EventclipRender")

    Events("onTestProgress")

    AsyncFunction("getEngineInfo") {
      val ctx = appContext.reactContext ?: throw IllegalStateException("No React context")
      deviceMeta() +
        mapOf(
          "engine" to "androidx.media3.transformer",
          "media3" to true,
          "media3Version" to "1.5.1",
          "ducking" to "VolumeEnvelopeAudioProcessor",
          "storageRoot" to SpikePaths.root(ctx).absolutePath,
          "storageNote" to
            "App-private filesDir only — NOT /data/local/tmp (not app-readable). No adb push required.",
          "debuggableExpected" to false,
          "priorExitInfo" to ExitInfoReader.recent(ctx),
        )
    }

    AsyncFunction("getPaths") {
      val ctx = appContext.reactContext ?: throw IllegalStateException("No React context")
      mapOf(
        "root" to SpikePaths.root(ctx).absolutePath,
        "fixtures" to SpikePaths.fixtures(ctx).absolutePath,
        "out" to SpikePaths.out(ctx).absolutePath,
        "logs" to SpikePaths.logs(ctx).absolutePath,
        "progress" to SpikePaths.progressFile(ctx).absolutePath,
        "latestResults" to SpikePaths.latestResultsFile(ctx).absolutePath,
      )
    }

    AsyncFunction("readProgress") {
      val ctx = appContext.reactContext ?: throw IllegalStateException("No React context")
      val f = SpikePaths.progressFile(ctx)
      if (!f.exists()) mapOf("exists" to false)
      else mapOf("exists" to true, "json" to f.readText())
    }

    AsyncFunction("cancelTests") {
      runner?.requestCancel()
      transformerExport.requestCancel()
      mapOf("ok" to true)
    }

    AsyncFunction("runAllTests") { promise: Promise ->
      val ctx = appContext.reactContext ?: run {
        promise.reject("NO_CTX", "No React context", null)
        return@AsyncFunction
      }
      executor.execute {
        try {
          transformerExport.resetCancel()
          val r = SpikePipelines.createScenarioRunner(ctx, transformerExport)
          runner = r
          val result =
            r.runAll { progress ->
              sendEvent("onTestProgress", progress)
            }
          runner = null
          promise.resolve(result)
        } catch (e: Exception) {
          runner = null
          promise.reject("TEST_FAILED", e.message, e)
        }
      }
    }

    AsyncFunction("run1080Once") { promise: Promise ->
      val ctx = appContext.reactContext ?: throw IllegalStateException("No React context")
      executor.execute {
        try {
          transformerExport.resetCancel()
          val r = SpikePipelines.createScenarioRunner(ctx,transformerExport)
          runner = r
          val result = r.runAll(focused1080 = true) { sendEvent("onTestProgress",it) }
          promise.resolve(result)
        } catch(e:Exception) { promise.reject("TEST_FAILED",ExportErrorDiagnostics.sanitizeMessage(e.message),e) }
        finally { runner = null }
      }
    }
    AsyncFunction("listOutputs") { promise: Promise ->
      val ctx = appContext.reactContext ?: throw IllegalStateException("No React context")
      executor.execute {
        try { val store=OutputStore(ctx);store.importVc4();promise.resolve(store.snapshot()) }
        catch(e:Exception){promise.reject("OUTPUTS_FAILED",e.javaClass.simpleName,e)}
      }
    }
    AsyncFunction("shareOutput") { selection: String, promise: Promise ->
      executor.execute {
        try { prepareShare(selection,promise) }
        catch(e:Exception){promise.reject("SHARE_FAILED",ExportErrorDiagnostics.sanitizeMessage(e.message),e)}
      }
    }

    AsyncFunction("compose") { request: ComposeRequest, promise: Promise ->
      // Always hop to worker: latch.await must never run on application Looper.
      executor.execute {
        try {
          promise.resolve(runCompose(request))
        } catch (e: Exception) {
          promise.reject("COMPOSE_FAILED", e.message, e)
        }
      }
    }

    AsyncFunction("analyzeBeats") { request: BeatRequest, promise: Promise ->
      try {
        promise.resolve(runBeats(request))
      } catch (e: Exception) {
        promise.reject("BEAT_FAILED", e.message, e)
      }
    }

    AsyncFunction("writeTextFile") { path: String, contents: String ->
      val file = File(path.removePrefix("file://"))
      file.parentFile?.mkdirs()
      file.writeText(contents)
      mapOf("ok" to true, "path" to file.absolutePath, "bytes" to file.length())
    }

    AsyncFunction("planDuration") { imageCount: Int, imageDur: Double, videoCount: Int, videoDur: Double ->
      val total = DuckEnvelope.expectedVisualDurationSec(imageCount, imageDur, videoCount, videoDur)
      mapOf(
        "plannedDurationSec" to total,
        "hits300" to (kotlin.math.abs(total - 300.0) < 0.05),
        "label" to "ARTIFICIAL timeline durations for spike plan",
      )
    }
  }

  private fun prepareShare(selection: String, promise: Promise) {
    val ctx=appContext.reactContext ?: throw IllegalStateException("No React context")
    val activity=appContext.currentActivity ?: throw IllegalStateException("No activity")
    val store=OutputStore(ctx)
    store.importVc4()
    val file: File
    val mime: String
    val identity: Map<String,Any?>
    if(selection == "json") {
      file=File(SpikePaths.logs(ctx),"share-results-vc5.json")
      val j=org.json.JSONObject()
      j.put("shareVersionCode",5)
      j.put("outputs",org.json.JSONObject(store.snapshot()))
      val latest=SpikePaths.latestResultsFile(ctx)
      if(latest.exists())j.put("latestResults",org.json.JSONObject(latest.readText()))
      // Include originating metrics for imported vc4 outputs, not just new run metrics.
      val old=SpikePaths.logs(ctx).listFiles()?.filter { it.name.startsWith("results_vc4_") && it.extension=="json" }?.sortedByDescending { it.lastModified() }?.take(3) ?: emptyList()
      j.put("priorVc4Results",org.json.JSONArray(old.map { org.json.JSONObject(it.readText()) }))
      file.writeText(j.toString(2))
      mime="application/json"
      identity=mapOf("selection" to "json","bytes" to file.length())
    } else {
      val chosen=store.selected(selection)
      file=chosen.first
      mime="video/mp4"
      identity=mapOf("selection" to selection,"basename" to file.name,"bytes" to file.length(),"sha256" to chosen.second.getString("sha256"),"runId" to chosen.second.getString("runId"))
    }
    val uri=FileProvider.getUriForFile(ctx,ctx.packageName+".fileprovider",file)
    val intent=Intent(Intent.ACTION_SEND).apply {
      type=mime
      putExtra(Intent.EXTRA_STREAM,uri)
      clipData=android.content.ClipData.newRawUri("EventClip diagnostic",uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      putExtra(Intent.EXTRA_SUBJECT,"EventClip diagnostic " + selection)
    }
    activity.runOnUiThread {
      try {
        activity.startActivity(Intent.createChooser(intent,"Share EventClip " + selection))
        promise.resolve(identity + mapOf("chooserOpened" to true,"sent" to false,"mime" to mime,"note" to "User chooses recipient; attachment limits vary; files retained."))
      } catch(e:Exception){promise.reject("SHARE_FAILED",e.javaClass.simpleName,e)}
    }
  }

  private fun deviceMeta(): Map<String, Any?> =
    mapOf(
      "manufacturer" to Build.MANUFACTURER,
      "model" to Build.MODEL,
      "sdkInt" to Build.VERSION.SDK_INT,
      "release" to Build.VERSION.RELEASE,
      "memoryCaveat" to
        "PSS sampled; not true allocator peak; incomplete across OEMs",
    )

  private fun runBeats(request: BeatRequest): Map<String, Any?> {
    val context = appContext.reactContext ?: throw IllegalStateException("No React context")
    return SpikePipelines.analyzeBeats(context, request)
  }

  private fun runCompose(request: ComposeRequest): Map<String, Any?> {
    val context = appContext.reactContext ?: throw IllegalStateException("No React context")
    return SpikePipelines.compose(context, transformerExport, request)
  }

  private fun makeWatermarkBitmap(text: String): Bitmap = SpikePipelines.makeWatermarkBitmap(text)
}


package com.eventclip.spike

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.eventcliprender.ClipRecord
import expo.modules.eventcliprender.ComposeRequest
import expo.modules.eventcliprender.ExportErrorDiagnostics
import expo.modules.eventcliprender.HandlerAppLooperGate
import expo.modules.eventcliprender.SpikePipelines
import expo.modules.eventcliprender.TransformerExport
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Deliberate invalid asset — diagnostics must be typed/useful without absolute paths. */
@RunWith(AndroidJUnit4::class)
class InvalidAssetDiagnosticsInstrumentedTest {
  @Test
  fun invalidAsset_surfacesTypedDiagnostics() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val bad = File(context.cacheDir, "deliberately_invalid.bin")
    bad.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
    val out = File(context.cacheDir, "should_fail.mp4")
    if (out.exists()) out.delete()

    val req =
      ComposeRequest().apply {
        clips =
          listOf(
            ClipRecord().apply {
              type = "image"
              uri = bad.absolutePath
              durationSec = 0.5
            },
          )
        songUri = bad.absolutePath
        width = 160
        height = 90
        fps = 24
        outputUri = out.absolutePath
        watermarkText = "diag"
      }

    val export = TransformerExport(HandlerAppLooperGate())
    val ex =
      Executors.newSingleThreadExecutor().submit<Throwable?> {
        try {
          SpikePipelines.compose(context, export, req)
          null
        } catch (t: Throwable) {
          t
        }
      }.get(90, TimeUnit.SECONDS)

    assertNotNull("expected failure", ex)
    val diag = ExportErrorDiagnostics.fromThrowable(ex!!, "deliberate_invalid", listOf(bad))
    android.util.Log.i("EventClipDiag", "invalidAsset diag=$diag")
    assertEquals("deliberate_invalid", diag["phase"])
    assertNotNull(diag["causeChain"])
    val fixtures = diag["fixtures"] as List<*>
    val meta = fixtures[0] as Map<*, *>
    assertEquals("deliberately_invalid.bin", meta["basename"])
    assertFalse(meta.toString().contains("/data/user/"))
    // Prefer Media3 typed fields when ExportException
    android.util.Log.i(
      "EventClipDiag",
      "media3ErrorCode=${diag["media3ErrorCode"]} name=${diag["media3ErrorCodeName"]} msg=${diag["errorMessage"]}",
    )
  }
}

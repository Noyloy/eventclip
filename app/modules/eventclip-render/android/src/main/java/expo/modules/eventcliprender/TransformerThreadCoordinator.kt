package expo.modules.eventcliprender

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Pure threading protocol for Media3 Transformer access:
 * create/start/cancel on [AppLooperGate]; await/completion on worker.
 * No Media3 types — JVM-testable. Real device integration is separate.
 */
class TransformerThreadCoordinator(
  private val gate: AppLooperGate,
) {
  data class Trace(
    val createOnAppLooper: Boolean,
    val startOnAppLooper: Boolean,
    val cancelOnAppLooper: Boolean,
    val awaitOnWorker: Boolean,
    val callbackDelivered: Boolean,
  )

  /**
   * @param workerThreadCheck returns true if current thread is allowed to await (not app looper)
   */
  fun <H> runExportCycle(
    isOnAppLooper: () -> Boolean,
    create: () -> H,
    start: (H) -> Unit,
    cancel: (H) -> Unit,
    /** Invoked once from create/start path to register how callbacks fire completion. */
    registerCompletion: (signalDone: () -> Unit, signalError: (Exception) -> Unit) -> Unit,
    awaitTimeoutMs: Long = 5_000,
    requestCancelAfterStart: Boolean = false,
  ): Pair<Trace, Exception?> {
    require(!isOnAppLooper()) {
      "await/export cycle must start on a worker thread — refusing to block application Looper"
    }
    val latch = CountDownLatch(1)
    val errorRef = AtomicReference<Exception?>(null)
    val createOnApp = AtomicBoolean(false)
    val startOnApp = AtomicBoolean(false)
    val cancelOnApp = AtomicBoolean(false)
    val callback = AtomicBoolean(false)

    val handle =
      gate.runOnAppLooper {
        createOnApp.set(true)
        registerCompletion(
          {
            callback.set(true)
            latch.countDown()
          },
          { ex ->
            callback.set(true)
            errorRef.set(ex)
            latch.countDown()
          },
        )
        val h = create()
        start(h)
        startOnApp.set(true)
        h
      }

    if (requestCancelAfterStart) {
      gate.runOnAppLooper {
        cancelOnApp.set(true)
        cancel(handle)
      }
    }

    val finished = latch.await(awaitTimeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) {
      gate.runOnAppLooper {
        cancelOnApp.set(true)
        cancel(handle)
      }
      return Pair(
        Trace(
          createOnAppLooper = createOnApp.get(),
          startOnAppLooper = startOnApp.get(),
          cancelOnAppLooper = cancelOnApp.get(),
          awaitOnWorker = true,
          callbackDelivered = callback.get(),
        ),
        IllegalStateException("timeout"),
      )
    }
    return Pair(
      Trace(
        createOnAppLooper = createOnApp.get(),
        startOnAppLooper = startOnApp.get(),
        cancelOnAppLooper = cancelOnApp.get(),
        awaitOnWorker = true,
        callbackDelivered = callback.get(),
      ),
      errorRef.get(),
    )
  }
}

/**
 * Scenario sequencing helpers (fixture progress + gate full renders after small failure).
 * Pure Kotlin — no Android.
 */
object ScenarioSequencer {
  const val FIXTURE_PROGRESS_READY = "fixtures_ready"
  const val FIXTURE_PROGRESS_FAILED = "fixtures_failed"
  const val SKIP_REASON = "skipped_after_compose_small_failure"

  fun fixtureProgressAfterPack(success: Boolean): String =
    if (success) FIXTURE_PROGRESS_READY else FIXTURE_PROGRESS_FAILED

  /** After compose_small, whether subsequent full renders should run. */
  fun shouldRunFullCompose(composeSmallOk: Boolean?): Boolean = composeSmallOk == true

  fun skipPayload(reason: String = SKIP_REASON): Map<String, Any?> =
    mapOf(
      "ok" to false,
      "status" to "skipped",
      "skipReason" to reason,
      "elapsedMsWall" to 0,
      "note" to "Full 720/1080 not started after compose_small failure — avoid misleading ~0.5s error repeats",
    )
}

package expo.modules.eventcliprender

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs Media3 Transformer create/start/cancel on the application main Looper.
 * Callers must NOT invoke [runOnAppLooper] while already holding a latch that the
 * posted block would wait on (deadlock). Prefer: post build/start/cancel only;
 * await completion on a worker thread.
 */
interface AppLooperGate {
  fun <T> runOnAppLooper(block: () -> T): T
}

class HandlerAppLooperGate(
  private val looper: Looper = Looper.getMainLooper(),
) : AppLooperGate {
  private val handler = Handler(looper)

  override fun <T> runOnAppLooper(block: () -> T): T {
    if (Looper.myLooper() == looper) {
      return block()
    }
    val done = CountDownLatch(1)
    val out = AtomicReference<T>()
    val err = AtomicReference<Throwable>()
    val posted =
      handler.post {
        try {
          out.set(block())
        } catch (t: Throwable) {
          err.set(t)
        } finally {
          done.countDown()
        }
      }
    if (!posted) {
      throw IllegalStateException("Failed to post to application Looper")
    }
    // Worker awaits — never call this from the app looper thread.
    if (!done.await(60, TimeUnit.SECONDS)) {
      throw IllegalStateException("Timed out waiting for application Looper work")
    }
    err.get()?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return out.get() as T
  }

  fun isOnAppLooper(): Boolean = Looper.myLooper() == looper

  fun looperThreadId(): Long = looper.thread.id
}

/**
 * Test double: records which synthetic "looper id" each call ran on.
 * Distinguishes JVM fake-thread tests from real Android/Media3 integration.
 */
class RecordingAppLooperGate(
  private val appLooperId: Long = 1L,
  private val currentLooperId: () -> Long,
) : AppLooperGate {
  data class Call(val op: String, val callerLooperId: Long, val ranOnLooperId: Long)

  val calls = mutableListOf<Call>()

  override fun <T> runOnAppLooper(block: () -> T): T {
    val caller = currentLooperId()
    // Simulate hop to app looper (always "runs" as appLooperId).
    calls.add(Call("runOnAppLooper", caller, appLooperId))
    return block()
  }
}

package expo.modules.eventcliprender

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * JVM fake-thread tests for Transformer create/start/cancel/callback protocol.
 * These do NOT run real Media3 — device/emulator integration is unavailable here.
 */
class TransformerThreadCoordinatorTest {
  private val APP = 1L
  private val WORKER = 2L
  private val current = AtomicLong(WORKER)

  private fun gate() =
    RecordingAppLooperGate(appLooperId = APP, currentLooperId = { current.get() })

  @Test
  fun createStartHappenViaAppLooperGate_awaitOnWorker() {
    val g = gate()
    val coord = TransformerThreadCoordinator(g)
    val createCount = AtomicInteger(0)
    val startCount = AtomicInteger(0)
    val signalRef = AtomicReference<(() -> Unit)?>(null)

    val (trace, err) =
      coord.runExportCycle(
        isOnAppLooper = { current.get() == APP },
        create = {
          assertEquals("create must be inside gate hop", APP, g.calls.last().ranOnLooperId)
          createCount.incrementAndGet()
          "handle"
        },
        start = { _ ->
          startCount.incrementAndGet()
          // Simulate Media3 callback shortly after start (often on app looper / internal).
          Thread {
            current.set(APP)
            signalRef.get()?.invoke()
            current.set(WORKER)
          }.start()
        },
        cancel = {},
        registerCompletion = { done, _ -> signalRef.set(done) },
        awaitTimeoutMs = 3_000,
      )

    assertNull(err)
    assertTrue(trace.createOnAppLooper)
    assertTrue(trace.startOnAppLooper)
    assertTrue(trace.awaitOnWorker)
    assertTrue(trace.callbackDelivered)
    assertEquals(1, createCount.get())
    assertEquals(1, startCount.get())
    assertTrue(g.calls.any { it.op == "runOnAppLooper" })
  }

  @Test
  fun timeoutCancelPostedThroughAppLooperGate() {
    val g = gate()
    val coord = TransformerThreadCoordinator(g)
    val cancelCount = AtomicInteger(0)

    val (trace, err) =
      coord.runExportCycle(
        isOnAppLooper = { current.get() == APP },
        create = { "h" },
        start = { _ -> /* never completes */ },
        cancel = { cancelCount.incrementAndGet() },
        registerCompletion = { _, _ -> },
        awaitTimeoutMs = 50,
      )

    assertNotNull(err)
    assertEquals("timeout", err!!.message)
    assertTrue(trace.cancelOnAppLooper)
    assertEquals(1, cancelCount.get())
    // create/start + cancel => at least 2 gate hops
    assertTrue(g.calls.size >= 2)
  }

  @Test
  fun refusesToStartCycleOnAppLooper_avoidsDeadlock() {
    current.set(APP)
    val g = gate()
    val coord = TransformerThreadCoordinator(g)
    try {
      coord.runExportCycle(
        isOnAppLooper = { current.get() == APP },
        create = { "h" },
        start = {},
        cancel = {},
        registerCompletion = { done, _ -> done() },
      )
      fail("expected refusal")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("worker"))
    } finally {
      current.set(WORKER)
    }
  }

  @Test
  fun recordingGateReportsCallerVsAppLooperIds() {
    val g = gate()
    current.set(WORKER)
    g.runOnAppLooper {
      assertEquals(WORKER, g.calls.last().callerLooperId)
      assertEquals(APP, g.calls.last().ranOnLooperId)
      42
    }
  }
}

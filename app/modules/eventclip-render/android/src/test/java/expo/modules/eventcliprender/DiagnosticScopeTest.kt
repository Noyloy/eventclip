package expo.modules.eventcliprender
import org.junit.Assert.*
import org.junit.Test
class DiagnosticScopeTest {
  @Test fun plannedDurationsMatch() {
    assertEquals(20.0, 10 * 2.0, 0.0)
    assertEquals(DiagnosticScope.MATCHED_SECONDS, 10 * 2.0, 0.0)
    assertEquals(600, (20.0 * 30).toInt())
    assertEquals(DiagnosticScope.FULL_SECONDS, 60 * 4.7 + 15 * 1.2, 0.000001)
  }
  @Test fun matchedFailureSkipsFull() {
    assertFalse(DiagnosticScope.matchedGate(false, 600, true))
    assertFalse(DiagnosticScope.matchedGate(true, null, true))
    assertFalse(DiagnosticScope.matchedGate(true, 599, true))
    assertFalse(DiagnosticScope.matchedGate(true, 600, false))
    assertTrue(DiagnosticScope.matchedGate(true, 600, true))
  }
}

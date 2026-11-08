package expo.modules.eventcliprender

import org.junit.Assert.*
import org.junit.Test

class DuckEnvelopeTest {
  @Test
  fun outsideRangesIsUnity() {
    val ranges = listOf(DuckRange(2.0, 4.0, 0.2))
    assertEquals(1.0, DuckEnvelope.gainAt(0.0, ranges, 0.05), 1e-9)
    assertEquals(1.0, DuckEnvelope.gainAt(10.0, ranges, 0.05), 1e-9)
  }

  @Test
  fun plateauUsesTargetGain() {
    val ranges = listOf(DuckRange(2.0, 4.0, 0.2))
    assertEquals(0.2, DuckEnvelope.gainAt(3.0, ranges, 0.05), 1e-9)
  }

  @Test
  fun rampIsMonotoneIntoDuck() {
    val ranges = listOf(DuckRange(1.0, 2.0, 0.0))
    val a = DuckEnvelope.gainAt(0.96, ranges, 0.05)
    val b = DuckEnvelope.gainAt(0.98, ranges, 0.05)
    val c = DuckEnvelope.gainAt(1.0, ranges, 0.05)
    assertTrue(a > b && b > c)
    assertEquals(0.0, c, 1e-9)
  }

  @Test
  fun overlappingTakesMinimum() {
    val ranges = listOf(DuckRange(1.0, 5.0, 0.5), DuckRange(2.0, 3.0, 0.1))
    assertEquals(0.1, DuckEnvelope.gainAt(2.5, ranges, 0.0), 1e-9)
  }

  @Test
  fun invalidRangesDropped() {
    val ranges = listOf(DuckRange(5.0, 1.0, 0.2), DuckRange(1.0, 2.0, 1.5))
    val g = DuckEnvelope.gainAt(1.5, ranges, 0.0)
    assertEquals(1.0, g, 1e-9) // gain coerced to 1.0 max, plateau 1.0
  }

  @Test
  fun gainClamped() {
    val ranges = listOf(DuckRange(0.0, 1.0, -2.0))
    assertEquals(0.0, DuckEnvelope.gainAt(0.5, DuckEnvelope.sanitize(ranges), 0.0), 1e-9)
  }

  @Test
  fun threeHundredSecondPlan() {
    val imageDur = DuckEnvelope.imageDurationForTarget(300.0, 60, 15, 1.2)
    assertEquals(4.7, imageDur, 1e-9)
    val total = DuckEnvelope.expectedVisualDurationSec(60, imageDur, 15, 1.2)
    assertEquals(300.0, total, 1e-9)
  }

  @Test
  fun boundaryExactlyAtEdges() {
    val ranges = listOf(DuckRange(2.0, 4.0, 0.25))
    assertEquals(0.25, DuckEnvelope.gainAt(2.0, ranges, 0.0), 1e-9)
    assertEquals(0.25, DuckEnvelope.gainAt(4.0, ranges, 0.0), 1e-9)
  }
}

package expo.modules.eventcliprender

import org.junit.Assert.*
import org.junit.Test

class ScenarioSequencerTest {
  @Test
  fun fixtureProgressReadyOnSuccess() {
    assertEquals("fixtures_ready", ScenarioSequencer.fixtureProgressAfterPack(true))
  }

  @Test
  fun fixtureProgressFailedOnFailure() {
    assertEquals("fixtures_failed", ScenarioSequencer.fixtureProgressAfterPack(false))
  }

  @Test
  fun fullComposeGatedUntilSmallOk() {
    assertFalse(ScenarioSequencer.shouldRunFullCompose(null))
    assertFalse(ScenarioSequencer.shouldRunFullCompose(false))
    assertTrue(ScenarioSequencer.shouldRunFullCompose(true))
  }

  @Test
  fun skipPayloadMarksSkippedNotErrorBenchmark() {
    val p = ScenarioSequencer.skipPayload()
    assertEquals(false, p["ok"])
    assertEquals("skipped", p["status"])
    assertEquals(ScenarioSequencer.SKIP_REASON, p["skipReason"])
    assertEquals(0, p["elapsedMsWall"])
  }
}

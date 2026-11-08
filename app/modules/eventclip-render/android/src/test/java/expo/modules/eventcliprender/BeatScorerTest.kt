package expo.modules.eventcliprender

import org.junit.Assert.*
import org.junit.Test

class BeatScorerTest {
  @Test
  fun perfectMatch() {
    val truth = listOf(0.0, 0.5, 1.0, 1.5)
    val det = listOf(0.01, 0.51, 1.02, 1.49)
    val s = BeatScorer.score(truth, det, 70.0)
    assertEquals(4, s.truePositives)
    assertEquals(1.0, s.fMeasure, 1e-9)
  }

  @Test
  fun halfTempoRelation() {
    assertEquals("half", BeatScorer.bpmRelation(60.0, 120.0))
    assertEquals("double", BeatScorer.bpmRelation(240.0, 120.0))
    assertEquals("match", BeatScorer.bpmRelation(121.0, 120.0))
  }

  @Test
  fun energyDetectorOnSyntheticClicks() {
    val sr = 44100
    val bpm = 120.0
    val period = 60.0 / bpm
    val durSec = 4.0
    val n = (sr * durSec).toInt()
    val samples = FloatArray(n)
    var t = 0.0
    while (t < durSec) {
      val start = (t * sr).toInt()
      val end = (start + (0.02 * sr).toInt()).coerceAtMost(n)
      for (i in start until end) {
        val local = (i - start).toDouble() / sr
        samples[i] = (0.8f * kotlin.math.sin(2.0 * Math.PI * 1000.0 * local)).toFloat()
      }
      t += period
    }
    val r = EnergyBeatDetector.detect(samples, sr)
    assertEquals("match", BeatScorer.bpmRelation(r.bpmEstimate, bpm))
    val truth = generateSequence(0.0) { it + period }.takeWhile { it < durSec - 1e-6 }.toList()
    val score = BeatScorer.score(truth, r.beatTimesSec, 70.0)
    assertTrue("fMeasure=${score.fMeasure}", score.fMeasure >= 0.8)
  }
}

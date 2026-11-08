package expo.modules.eventcliprender

import kotlin.math.abs

data class BeatScore(
  val toleranceMs: Double,
  val trueCount: Int,
  val detectedCount: Int,
  val truePositives: Int,
  val falsePositives: Int,
  val falseNegatives: Int,
  val precision: Double,
  val recall: Double,
  val fMeasure: Double,
  val meanAbsErrorMs: Double?,
)

object BeatScorer {
  fun score(truthSec: List<Double>, detectedSec: List<Double>, toleranceMs: Double): BeatScore {
    val tol = toleranceMs / 1000.0
    val used = HashSet<Int>()
    var tp = 0
    val errors = ArrayList<Double>()
    for (t in truthSec) {
      var best = -1
      var bestErr = Double.POSITIVE_INFINITY
      for (i in detectedSec.indices) {
        if (used.contains(i)) continue
        val err = abs(detectedSec[i] - t)
        if (err <= tol && err < bestErr) {
          bestErr = err
          best = i
        }
      }
      if (best >= 0) {
        used.add(best)
        tp++
        errors.add(bestErr * 1000.0)
      }
    }
    val fp = detectedSec.size - tp
    val fn = truthSec.size - tp
    val precision = tp.toDouble() / maxOf(1, tp + fp)
    val recall = tp.toDouble() / maxOf(1, tp + fn)
    val f = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    return BeatScore(
      toleranceMs,
      truthSec.size,
      detectedSec.size,
      tp,
      fp,
      fn,
      precision,
      recall,
      f,
      if (errors.isEmpty()) null else errors.average(),
    )
  }

  fun bpmRelation(estimated: Double, truth: Double): String {
    if (truth <= 0) return "other"
    val rel = estimated / truth
    return when {
      abs(rel - 1.0) <= 0.03 -> "match"
      abs(rel - 0.5) <= 0.03 -> "half"
      abs(rel - 2.0) <= 0.03 -> "double"
      else -> "other"
    }
  }
}

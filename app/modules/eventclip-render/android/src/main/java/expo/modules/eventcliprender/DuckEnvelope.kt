package expo.modules.eventcliprender

import kotlin.math.max
import kotlin.math.min

data class DuckRange(
  val startSec: Double,
  val endSec: Double,
  val gain: Double,
)

/**
 * Piecewise volume envelope with linear ramps into/out of each duck range.
 * Outside ranges gain = 1.0. Overlapping ranges take the minimum gain.
 */
object DuckEnvelope {
  fun sanitize(ranges: List<DuckRange>): List<DuckRange> {
    return ranges
      .mapNotNull { r ->
        if (r.endSec <= r.startSec) return@mapNotNull null
        DuckRange(r.startSec, r.endSec, r.gain.coerceIn(0.0, 1.0))
      }
      .sortedBy { it.startSec }
  }

  /**
   * @param rampSec linear fade duration at each edge (clamped so it cannot exceed half the range).
   */
  fun gainAt(timeSec: Double, ranges: List<DuckRange>, rampSec: Double = 0.05): Double {
    if (timeSec.isNaN() || timeSec.isInfinite()) return 1.0
    val ramp = max(0.0, rampSec)
    var gain = 1.0
    for (r in sanitize(ranges)) {
      val half = (r.endSec - r.startSec) / 2.0
      val edge = min(ramp, half)
      val g =
        when {
          timeSec < r.startSec - edge -> 1.0
          timeSec < r.startSec -> {
            // ramp down from 1 → target
            val t = (timeSec - (r.startSec - edge)) / edge
            lerp(1.0, r.gain, t)
          }
          timeSec <= r.endSec -> r.gain
          timeSec < r.endSec + edge -> {
            val t = (timeSec - r.endSec) / edge
            lerp(r.gain, 1.0, t)
          }
          else -> 1.0
        }
      gain = min(gain, g)
    }
    return gain.coerceIn(0.0, 1.0)
  }

  fun expectedVisualDurationSec(imageCount: Int, imageDur: Double, videoCount: Int, videoDur: Double): Double {
    return imageCount * imageDur + videoCount * videoDur
  }

  /** Durations that hit targetSec with 60 images + 15 clips of fixed clip length. */
  fun imageDurationForTarget(targetSec: Double, images: Int = 60, clips: Int = 15, clipDur: Double = 1.2): Double {
    require(images > 0)
    return (targetSec - clips * clipDur) / images
  }

  private fun lerp(a: Double, b: Double, t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return a + (b - a) * x
  }
}

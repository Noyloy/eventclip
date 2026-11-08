package expo.modules.eventcliprender

import kotlin.math.max

data class BeatResult(
  val beatTimesSec: List<Double>,
  val bpmEstimate: Double,
  val method: String,
)

object EnergyBeatDetector {
  fun detect(
    samples: FloatArray,
    sampleRate: Int,
    hopSec: Double = 0.01,
    windowSec: Double = 0.04,
    minBpm: Double = 70.0,
    maxBpm: Double = 180.0,
  ): BeatResult {
    val hop = max(1, (sampleRate * hopSec).toInt())
    val win = max(hop, (sampleRate * windowSec).toInt())
    val energies = ArrayList<Double>()
    var i = 0
    while (i + win < samples.size) {
      var e = 0.0
      for (j in 0 until win) {
        val v = samples[i + j].toDouble()
        e += v * v
      }
      energies.add(e / win)
      i += hop
    }
    if (energies.size < 4) {
      return BeatResult(emptyList(), 0.0, "energy_flux_tempo_lag_v1")
    }
    val flux = DoubleArray(energies.size)
    for (k in 1 until energies.size) {
      flux[k] = max(0.0, energies[k] - energies[k - 1])
    }
    val minLag = max(1, (60.0 / maxBpm / hopSec).toInt())
    val maxLag = max(minLag, (60.0 / minBpm / hopSec).toInt())
    var bestLag = minLag
    var bestScore = Double.NEGATIVE_INFINITY
    for (lag in minLag..maxLag) {
      var score = 0.0
      var idx = 0
      while (idx + lag < flux.size) {
        score += flux[idx] * flux[idx + lag]
        idx++
      }
      if (score > bestScore) {
        bestScore = score
        bestLag = lag
      }
    }
    val bpm = 60.0 / (bestLag * hopSec)
    val refractory = max(1, (bestLag * 0.6).toInt())
    val mean = flux.sum() / flux.size
    val threshold = mean * 1.5
    val beats = ArrayList<Double>()
    var last = -refractory
    for (k in 1 until flux.size - 1) {
      if (k - last < refractory) continue
      val v = flux[k]
      if (v >= threshold && v >= flux[k - 1] && v >= flux[k + 1]) {
        beats.add(k * hopSec)
        last = k
      }
    }
    return BeatResult(beats, bpm, "energy_flux_tempo_lag_v1")
  }
}

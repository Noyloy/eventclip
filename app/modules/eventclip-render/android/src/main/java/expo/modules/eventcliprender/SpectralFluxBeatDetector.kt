package expo.modules.eventcliprender

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.PI

/**
 * Second local approach: short-time spectral flux (magnitude spectrum diffs) + tempo lag.
 * Uses a tiny DFT window (not FFTW) — OK for spike correctness, not a mobile perf claim.
 */
object SpectralFluxBeatDetector {
  fun detect(
    samples: FloatArray,
    sampleRate: Int,
    hopSec: Double = 0.01,
    windowSize: Int = 512,
    minBpm: Double = 70.0,
    maxBpm: Double = 180.0,
  ): BeatResult {
    val hop = max(1, (sampleRate * hopSec).toInt())
    val n = windowSize
    if (samples.size < n * 2) {
      return BeatResult(emptyList(), 0.0, "spectral_flux_tempo_lag_v1")
    }
    val mags = ArrayList<DoubleArray>()
    var i = 0
    while (i + n < samples.size) {
      val re = DoubleArray(n / 2)
      val im = DoubleArray(n / 2)
      // naive DFT of first n/2 bins
      for (k in re.indices) {
        var rr = 0.0
        var ii = 0.0
        var t = 0
        while (t < n) {
          val ang = 2.0 * PI * k * t / n
          val s = samples[i + t].toDouble()
          rr += s * cos(ang)
          ii -= s * sin(ang)
          t++
        }
        re[k] = rr
        im[k] = ii
      }
      val mag = DoubleArray(re.size) { k -> kotlin.math.sqrt(re[k] * re[k] + im[k] * im[k]) }
      mags.add(mag)
      i += hop
    }
    val flux = DoubleArray(mags.size)
    for (f in 1 until mags.size) {
      var sum = 0.0
      val a = mags[f]
      val b = mags[f - 1]
      for (k in a.indices) {
        sum += max(0.0, a[k] - b[k])
      }
      flux[f] = sum
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
    val mean = flux.sum() / max(1, flux.size)
    val threshold = mean * 1.4
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
    return BeatResult(beats, bpm, "spectral_flux_tempo_lag_v1")
  }
}

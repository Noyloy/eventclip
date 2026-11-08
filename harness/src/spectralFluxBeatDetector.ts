/** Host-side spectral flux detector mirroring Kotlin SpectralFluxBeatDetector (correctness only). */
export type BeatResult = { beatTimesSec: number[]; bpmEstimate: number; method: string };

export function detectBeatsSpectral(
  samples: Float32Array,
  sampleRate: number,
  hopSec = 0.01,
  windowSize = 512,
  minBpm = 70,
  maxBpm = 180
): BeatResult {
  const hop = Math.max(1, Math.floor(sampleRate * hopSec));
  const n = windowSize;
  if (samples.length < n * 2) return { beatTimesSec: [], bpmEstimate: 0, method: "spectral_flux_tempo_lag_v1" };
  const mags: number[][] = [];
  for (let i = 0; i + n < samples.length; i += hop) {
    const mag = new Array(n / 2).fill(0);
    for (let k = 0; k < n / 2; k++) {
      let re = 0, im = 0;
      for (let t = 0; t < n; t++) {
        const ang = (2 * Math.PI * k * t) / n;
        const s = samples[i + t]!;
        re += s * Math.cos(ang);
        im -= s * Math.sin(ang);
      }
      mag[k] = Math.hypot(re, im);
    }
    mags.push(mag);
  }
  const flux = new Array(mags.length).fill(0);
  for (let f = 1; f < mags.length; f++) {
    let sum = 0;
    for (let k = 0; k < mags[f]!.length; k++) sum += Math.max(0, mags[f]![k]! - mags[f - 1]![k]!);
    flux[f] = sum;
  }
  const minLag = Math.max(1, Math.floor(60 / maxBpm / hopSec));
  const maxLag = Math.max(minLag, Math.floor(60 / minBpm / hopSec));
  let bestLag = minLag, bestScore = -Infinity;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let score = 0;
    for (let i = 0; i + lag < flux.length; i++) score += flux[i]! * flux[i + lag]!;
    if (score > bestScore) { bestScore = score; bestLag = lag; }
  }
  const bpmEstimate = 60 / (bestLag * hopSec);
  const refractory = Math.max(1, Math.floor(bestLag * 0.6));
  const mean = flux.reduce((a, b) => a + b, 0) / Math.max(1, flux.length);
  const threshold = mean * 1.4;
  const beatTimesSec: number[] = [];
  let last = -refractory;
  for (let i = 1; i < flux.length - 1; i++) {
    if (i - last < refractory) continue;
    const v = flux[i]!;
    if (v >= threshold && v >= flux[i - 1]! && v >= flux[i + 1]!) {
      beatTimesSec.push(i * hopSec);
      last = i;
    }
  }
  return { beatTimesSec, bpmEstimate, method: "spectral_flux_tempo_lag_v1" };
}

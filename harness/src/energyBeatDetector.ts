/**
 * Baseline on-device-shaped beat detector (energy flux + tempo lag).
 * Pure TS — same algorithm intended for RN port. Not a mobile benchmark when run on the Linux host.
 */
export type BeatResult = {
  beatTimesSec: number[];
  bpmEstimate: number;
  method: "energy_flux_tempo_lag_v1";
};

export type BeatDetectorOptions = {
  hopSec?: number;
  windowSec?: number;
  minBpm?: number;
  maxBpm?: number;
};

export function detectBeatsEnergy(
  samples: Float32Array,
  sampleRate: number,
  opts: BeatDetectorOptions = {}
): BeatResult {
  const hopSec = opts.hopSec ?? 0.01;
  const windowSec = opts.windowSec ?? 0.04;
  const minBpm = opts.minBpm ?? 70;
  const maxBpm = opts.maxBpm ?? 180;
  const hop = Math.max(1, Math.floor(sampleRate * hopSec));
  const win = Math.max(hop, Math.floor(sampleRate * windowSec));

  const energies: number[] = [];
  for (let i = 0; i + win < samples.length; i += hop) {
    let e = 0;
    for (let j = 0; j < win; j++) {
      const v = samples[i + j]!;
      e += v * v;
    }
    energies.push(e / win);
  }

  const flux: number[] = [0];
  for (let i = 1; i < energies.length; i++) {
    flux.push(Math.max(0, energies[i]! - energies[i - 1]!));
  }

  // Tempo lag autocorrelation on flux
  const minLag = Math.floor(60 / maxBpm / hopSec);
  const maxLag = Math.floor(60 / minBpm / hopSec);
  let bestLag = minLag;
  let bestScore = -Infinity;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let score = 0;
    for (let i = 0; i + lag < flux.length; i++) {
      score += flux[i]! * flux[i + lag]!;
    }
    if (score > bestScore) {
      bestScore = score;
      bestLag = lag;
    }
  }
  const bpmEstimate = 60 / (bestLag * hopSec);

  // Peak pick with refractory period ~ 60% of beat period
  const refractory = Math.max(1, Math.floor(bestLag * 0.6));
  const mean = flux.reduce((a, b) => a + b, 0) / Math.max(1, flux.length);
  const threshold = mean * 1.5;
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

  return { beatTimesSec, bpmEstimate, method: "energy_flux_tempo_lag_v1" };
}

/** Half/double-aware BPM match within 3%. */
export function bpmRelation(
  estimated: number,
  truth: number
): "match" | "half" | "double" | "other" {
  const rel = estimated / truth;
  if (Math.abs(rel - 1) <= 0.03) return "match";
  if (Math.abs(rel - 0.5) <= 0.03) return "half";
  if (Math.abs(rel - 2) <= 0.03) return "double";
  return "other";
}

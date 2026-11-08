export type Score = {
  toleranceMs: number;
  trueCount: number;
  detectedCount: number;
  truePositives: number;
  falsePositives: number;
  falseNegatives: number;
  precision: number;
  recall: number;
  fMeasure: number;
  meanAbsErrorMs: number | null;
};

/** Greedy 1:1 matching within tolerance. */
export function scoreBeats(
  truthSec: number[],
  detectedSec: number[],
  toleranceMs: number
): Score {
  const tol = toleranceMs / 1000;
  const usedDet = new Set<number>();
  let tp = 0;
  const errors: number[] = [];
  for (const t of truthSec) {
    let best = -1;
    let bestErr = Infinity;
    for (let i = 0; i < detectedSec.length; i++) {
      if (usedDet.has(i)) continue;
      const err = Math.abs(detectedSec[i]! - t);
      if (err <= tol && err < bestErr) {
        bestErr = err;
        best = i;
      }
    }
    if (best >= 0) {
      usedDet.add(best);
      tp += 1;
      errors.push(bestErr * 1000);
    }
  }
  const fp = detectedSec.length - tp;
  const fn = truthSec.length - tp;
  const precision = tp / Math.max(1, tp + fp);
  const recall = tp / Math.max(1, tp + fn);
  const fMeasure = precision + recall === 0 ? 0 : (2 * precision * recall) / (precision + recall);
  return {
    toleranceMs,
    trueCount: truthSec.length,
    detectedCount: detectedSec.length,
    truePositives: tp,
    falsePositives: fp,
    falseNegatives: fn,
    precision,
    recall,
    fMeasure,
    meanAbsErrorMs: errors.length ? errors.reduce((a, b) => a + b, 0) / errors.length : null,
  };
}

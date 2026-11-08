import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { performance } from "node:perf_hooks";
import { readWavPcm } from "./readWavPcm.ts";
import { detectBeatsEnergy, bpmRelation } from "./energyBeatDetector.ts";
import { detectBeatsSpectral } from "./spectralFluxBeatDetector.ts";
import { scoreBeats } from "./scoreBeats.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, "../..");
const fixtures = resolve(root, "fixtures");
const outDir = resolve(root, "reports");
mkdirSync(outDir, { recursive: true });

type Gt = {
  bpm_true: number | null;
  beat_times_sec: number[];
  tolerance_ms_primary: number;
  tolerance_ms_strict: number;
  file_stem: string;
  note?: string;
  alt_bpm?: number;
};

function runOne(stem: string, wavRel: string) {
  const gt = JSON.parse(readFileSync(resolve(fixtures, "ground-truth", `${stem}.beats.json`), "utf8")) as Gt;
  const { sampleRate, samples } = readWavPcm(resolve(fixtures, wavRel));
  const t0 = performance.now();
  const energy = detectBeatsEnergy(samples, sampleRate);
  const t1 = performance.now();
  // Spectral DFT is O(n*N*bins); skip on long host fixtures (still covered by 8s cases + JVM energy test)
  const runSpectral = samples.length / sampleRate <= 12;
  const spectral = runSpectral
    ? detectBeatsSpectral(samples, sampleRate)
    : { beatTimesSec: [] as number[], bpmEstimate: 0, method: "spectral_flux_tempo_lag_v1_skipped_long" };
  const t2 = performance.now();
  const primaryE = scoreBeats(gt.beat_times_sec, energy.beatTimesSec, gt.tolerance_ms_primary);
  const primaryS = scoreBeats(gt.beat_times_sec, spectral.beatTimesSec, gt.tolerance_ms_primary);
  return {
    environment: "linux_host_node",
    environment_note: "HOST_NOT_DEVICE — synthetic correctness only; not real-song accuracy.",
    stem,
    note: gt.note ?? null,
    sampleRate,
    durationSec: samples.length / sampleRate,
    energy: {
      bpmEstimate: energy.bpmEstimate,
      bpmRelation: gt.bpm_true == null ? null : bpmRelation(energy.bpmEstimate, gt.bpm_true),
      altRelation: gt.alt_bpm != null ? bpmRelation(energy.bpmEstimate, gt.alt_bpm) : null,
      detectMs: t1 - t0,
      primary: primaryE,
    },
    spectral: {
      bpmEstimate: spectral.bpmEstimate,
      bpmRelation: gt.bpm_true == null ? null : bpmRelation(spectral.bpmEstimate, gt.bpm_true),
      altRelation: gt.alt_bpm != null ? bpmRelation(spectral.bpmEstimate, gt.alt_bpm) : null,
      detectMs: t2 - t1,
      primary: primaryS,
    },
  };
}

const cases = [
  ["metronome_120bpm_8s", "audio/metronome_120bpm_8s.wav"],
  ["metronome_120bpm_210s", "audio/metronome_120bpm_210s.wav"],
  ["weak_120bpm_8s", "audio/weak_120bpm_8s.wav"],
  ["silence_with_blip_8s", "audio/silence_with_blip_8s.wav"],
  ["tempo_ambiguous_8s", "audio/tempo_ambiguous_8s.wav"],
  ["offbeat_irregular_8s", "audio/offbeat_irregular_8s.wav"],
] as const;

const results = cases.map(([stem, wav]) => runOne(stem, wav));
const report = {
  title: "EventClip spike — beat detection host measurements (expanded)",
  measured_at: new Date().toISOString(),
  label: "HOST_NOT_DEVICE_SYNTHETIC_CORRECTNESS",
  distinction:
    "Synthetic fixture correctness ≠ real-song accuracy. Real DRM-free songs remain unmeasured until on-device.",
  results,
};
writeFileSync(resolve(outDir, "beat-host-results.json"), JSON.stringify(report, null, 2) + "\n");
console.log(JSON.stringify(report, null, 2));

/** Cross-platform composition interfaces (Android Media3 impl first). */
export type DuckRange = { startSec: number; endSec: number; gain: number };

export type ComposeClip =
  | { type: "image"; uri: string; durationSec: number }
  | { type: "video"; uri: string; durationSec?: number };

export type ComposeSpec = {
  clips: ComposeClip[];
  songUri: string;
  duckRanges: DuckRange[];
  watermarkText: string;
  width: number;
  height: number;
  fps: number;
  outputUri: string;
  duckRampSec?: number;
};

export const FULL_TARGET_SEC = 300;
export const FULL_IMAGE_COUNT = 60;
export const FULL_CLIP_COUNT = 15;
export const FULL_CLIP_DUR = 1.2;
/** (300 - 15*1.2) / 60 = 4.7 */
export const FULL_IMAGE_DUR = (FULL_TARGET_SEC - FULL_CLIP_COUNT * FULL_CLIP_DUR) / FULL_IMAGE_COUNT;

export function plannedDurationSec(spec: ComposeSpec): number {
  return spec.clips.reduce((a, c) => a + (c.durationSec ?? 0), 0);
}

export function assertComposeSpec(spec: ComposeSpec): string[] {
  const errors: string[] = [];
  if (!spec.clips.length) errors.push("clips empty");
  if (!spec.songUri) errors.push("songUri required");
  if (spec.width < 16 || spec.height < 16) errors.push("invalid resolution");
  for (const d of spec.duckRanges) {
    if (d.endSec <= d.startSec) errors.push(`bad duck range ${d.startSec}-${d.endSec}`);
    if (d.gain < 0 || d.gain > 1) errors.push("duck gain must be 0..1");
  }
  if (!spec.watermarkText) errors.push("watermarkText required");
  return errors;
}

export function buildFullSpikeSpec(fixtureRoot: string, res: "720p" | "1080p"): ComposeSpec {
  const clips: ComposeClip[] = [];
  for (let i = 1; i <= FULL_IMAGE_COUNT; i++) {
    const id = String(i).padStart(2, "0");
    clips.push({
      type: "image",
      uri: `${fixtureRoot}/images/photo_${id}.jpg`,
      durationSec: FULL_IMAGE_DUR,
    });
  }
  for (let i = 1; i <= FULL_CLIP_COUNT; i++) {
    const id = String(i).padStart(2, "0");
    clips.push({ type: "video", uri: `${fixtureRoot}/clips/clip_${id}.mp4`, durationSec: FULL_CLIP_DUR });
  }
  const dim = res === "720p" ? { width: 720, height: 1280 } : { width: 1080, height: 1920 };
  return {
    clips,
    songUri: `${fixtureRoot}/audio/metronome_120bpm_300s.mp3`,
    duckRanges: [
      { startSec: 30, endSec: 45, gain: 0.15 },
      { startSec: 90, endSec: 105, gain: 0.15 },
      { startSec: 200, endSec: 220, gain: 0.2 },
    ],
    watermarkText: "Made with EventClip",
    ...dim,
    fps: 30,
    duckRampSec: 0.05,
    outputUri: `${fixtureRoot}/../reports/out_${res}.mp4`,
  };
}

export function buildSmallSpec(fixtureRoot: string): ComposeSpec {
  const clips: ComposeClip[] = [];
  for (let i = 1; i <= 6; i++) {
    const id = String(i).padStart(2, "0");
    clips.push({ type: "image", uri: `${fixtureRoot}/small/img_${id}.jpg`, durationSec: 0.8 });
  }
  for (let i = 1; i <= 3; i++) {
    const id = String(i).padStart(2, "0");
    clips.push({ type: "video", uri: `${fixtureRoot}/small/clip_${id}.mp4`, durationSec: 1.0 });
  }
  return {
    clips,
    songUri: `${fixtureRoot}/audio/metronome_120bpm_8s.mp3`,
    duckRanges: [{ startSec: 2, endSec: 4, gain: 0.2 }],
    watermarkText: "Made with EventClip",
    width: 640,
    height: 360,
    fps: 30,
    duckRampSec: 0.05,
    outputUri: `${fixtureRoot}/../reports/out_small.mp4`,
  };
}

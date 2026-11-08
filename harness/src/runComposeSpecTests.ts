import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { writeFileSync, mkdirSync, existsSync } from "node:fs";
import {
  assertComposeSpec,
  buildFullSpikeSpec,
  buildSmallSpec,
  plannedDurationSec,
  FULL_TARGET_SEC,
  FULL_IMAGE_DUR,
} from "./composeTypes.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, "../..");
const fixtures = resolve(root, "fixtures");
const outDir = resolve(root, "reports");
mkdirSync(outDir, { recursive: true });

const small = buildSmallSpec(fixtures);
const full720 = buildFullSpikeSpec(fixtures, "720p");
const full1080 = buildFullSpikeSpec(fixtures, "1080p");

function checkFiles(spec: ReturnType<typeof buildSmallSpec>) {
  const missing: string[] = [];
  for (const c of spec.clips) if (!existsSync(c.uri)) missing.push(c.uri);
  if (!existsSync(spec.songUri)) missing.push(spec.songUri);
  return missing;
}

const d720 = plannedDurationSec(full720);
const report = {
  title: "Compose spec validation",
  fullImageDur: FULL_IMAGE_DUR,
  targetSec: FULL_TARGET_SEC,
  full720PlannedSec: d720,
  hits300: Math.abs(d720 - FULL_TARGET_SEC) < 1e-9,
  small: { errors: assertComposeSpec(small), missing: checkFiles(small), clipCount: small.clips.length, plannedSec: plannedDurationSec(small) },
  full720: {
    errors: assertComposeSpec(full720),
    missing: checkFiles(full720),
    clipCount: full720.clips.length,
    imageCount: full720.clips.filter((c) => c.type === "image").length,
    videoCount: full720.clips.filter((c) => c.type === "video").length,
    plannedSec: d720,
  },
  full1080: { errors: assertComposeSpec(full1080), missing: checkFiles(full1080), plannedSec: plannedDurationSec(full1080) },
};

writeFileSync(resolve(outDir, "compose-spec-validation.json"), JSON.stringify(report, null, 2) + "\n");
writeFileSync(resolve(outDir, "compose-spec-small.json"), JSON.stringify(small, null, 2) + "\n");
writeFileSync(resolve(outDir, "compose-spec-720p.json"), JSON.stringify(full720, null, 2) + "\n");
writeFileSync(resolve(outDir, "compose-spec-1080p.json"), JSON.stringify(full1080, null, 2) + "\n");
console.log(JSON.stringify(report, null, 2));
if (!report.hits300) process.exit(2);

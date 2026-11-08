import { readFileSync } from "node:fs";

/** Minimal PCM s16le WAV reader (mono or stereo → mono float32). Host harness only. */
export function readWavPcm(path: string): { sampleRate: number; samples: Float32Array } {
  const buf = readFileSync(path);
  if (buf.toString("ascii", 0, 4) !== "RIFF" || buf.toString("ascii", 8, 12) !== "WAVE") {
    throw new Error(`Not a RIFF/WAVE file: ${path}`);
  }
  let offset = 12;
  let sampleRate = 44100;
  let channels = 1;
  let bitsPerSample = 16;
  let dataOffset = -1;
  let dataSize = 0;
  while (offset + 8 <= buf.length) {
    const id = buf.toString("ascii", offset, offset + 4);
    const size = buf.readUInt32LE(offset + 4);
    const start = offset + 8;
    if (id === "fmt ") {
      channels = buf.readUInt16LE(start + 2);
      sampleRate = buf.readUInt32LE(start + 4);
      bitsPerSample = buf.readUInt16LE(start + 14);
    } else if (id === "data") {
      dataOffset = start;
      dataSize = size;
      break;
    }
    offset = start + size + (size % 2);
  }
  if (dataOffset < 0) throw new Error("No data chunk");
  if (bitsPerSample !== 16) throw new Error(`Unsupported bitsPerSample=${bitsPerSample}`);
  const frameCount = Math.floor(dataSize / (2 * channels));
  const samples = new Float32Array(frameCount);
  for (let i = 0; i < frameCount; i++) {
    let acc = 0;
    for (let c = 0; c < channels; c++) {
      acc += buf.readInt16LE(dataOffset + (i * channels + c) * 2) / 32768;
    }
    samples[i] = acc / channels;
  }
  return { sampleRate, samples };
}

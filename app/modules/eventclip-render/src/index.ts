import { requireNativeModule, Platform, EventEmitter, type EventSubscription } from "expo-modules-core";

type Native = {
  getEngineInfo(): Promise<Record<string, unknown>>;
  getPaths(): Promise<Record<string, unknown>>;
  readProgress(): Promise<Record<string, unknown>>;
  cancelTests(): Promise<Record<string, unknown>>;
  runAllTests(): Promise<Record<string, unknown>>;
  run1080Once(): Promise<Record<string, unknown>>;
  listOutputs(): Promise<Record<string, unknown>>;
  shareOutput(selection: string): Promise<Record<string, unknown>>;
  planDuration(a: number, b: number, c: number, d: number): Promise<Record<string, unknown>>;
  writeTextFile(path: string, contents: string): Promise<Record<string, unknown>>;
};

const fallback: Native = {
  async getEngineInfo() {
    return { engine: "unavailable", platform: Platform.OS };
  },
  async getPaths() {
    return {};
  },
  async readProgress() {
    return { exists: false };
  },
  async cancelTests() {
    return { ok: false };
  },
  async runAllTests() {
    return { ok: false, detail: "native missing" };
  },
  async run1080Once() { return { ok: false }; },
  async listOutputs() { return {}; },
  async shareOutput() {
    return { ok: false };
  },
  async planDuration(a, b, c, d) {
    return { plannedDurationSec: a * b + c * d };
  },
  async writeTextFile() {
    return { ok: false };
  },
};

let native: Native = fallback;
type ProgressEvents = { onTestProgress: (event: Record<string, unknown>) => void };
let emitter: EventEmitter<ProgressEvents> | null = null;
try {
  if (Platform.OS === "android") {
    native = requireNativeModule<Native>("EventclipRender");
    emitter = new EventEmitter<ProgressEvents>(native as any);
  }
} catch {
  native = fallback;
}

export const getEngineInfo = () => native.getEngineInfo();
export const getPaths = () => native.getPaths();
export const readProgress = () => native.readProgress();
export const cancelTests = () => native.cancelTests();
export const runAllTests = () => native.runAllTests();
export const run1080Once = () => native.run1080Once();
export const listOutputs = () => native.listOutputs();
export const shareOutput = (selection: "json" | "720" | "1080") => native.shareOutput(selection);
export const planDuration = (a: number, b: number, c: number, d: number) =>
  native.planDuration(a, b, c, d);
export const writeTextFileNative = (path: string, contents: string) =>
  native.writeTextFile(path, contents);

export function addTestProgressListener(listener: (event: Record<string, unknown>) => void): EventSubscription | null {
  if (!emitter) return null;
  return emitter.addListener("onTestProgress", listener);
}

> Latest public status: HELD vc8 reached EXPORT_READY at 720×1280 on S23, then failed the official native decode gate with a 40 ms audio lag (same numbers as the software emulator). That offset is unsignaled AAC-LC encoder priming muxed as content at PTS 0, with no edit list. HELD vc9 trims that priming from the muxed timeline (and flushes the delayed tail) so the decoded audio is aligned even when a player ignores edit lists. A later official S23 selected-method run of vc9 passed the same unchanged decode-gate thresholds (lag 0 ms, unmarked 1.007, marked 0.993, final 0.996, correlation about 0.99983, all windows in range). Product APKs remain HELD. This is not listening, visual, Save, or Share acceptance. No raw private execution identifiers are published.

# EventClip build handoff: vc9 — HELD

- Version code 9; version 1.5.1-private; package com.eventclip.spike; arm64-v8a.
- Date: 2026-11-11 13:27 Asia/Jerusalem; built locally/offline by Engineer.
- App: `<PROJECT_ROOT>/reports/vc9/EventClipSpike-offline-arm64-vc9-v1.5.1-private-HELD.apk`
- SHA256 `c9c2a58f457f90005273458d9992bdba766c4500dc4d6cd672dda60a61fba51c`; 13,349,135 bytes (<24,000,000).
- Matching RELEASE test: `<PROJECT_ROOT>/reports/vc9/EventClipSpike-arm64-vc9-release-androidTest-HELD.apk`
- SHA256 `bd6d0dfb9d2cf87d165b7a9a97a07a185819c9444c88b2fef66f7f3793941d5e`; 2,952,455 bytes (identical to the vc8 test APK; only product code changed).
- Both verified certificate SHA256 `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c` matches held vc5–vc8.
- Official S23 selected-method decode gate: PASS on 2026-11-11 13:30 Asia/Jerusalem (thresholds unchanged).
- **HELD: do NOT hand to Owner/install/uninstall/clear data/upload/submit without authorization.** Use both vc9 files together, not vc8.

## Changelog (two lines max)
- BUG-003: AAC-LC encoder priming (~2048 samples) is padded through EOS, dropped from the muxed stream, and PTS rebased to 0 so decoded audio is in sync without relying on an edit list.
- Source gapless guard, 21000 ms mux watchdog, 720×1280 portrait encoding, common postmix 0.5, and runner-boundary rules unchanged. Diagnostic default remains vc5.

## Known issues
- Official S23 and local software-emulator selected-method results are synthetic native-decode-gate evidence only. Not listening/visual acceptance, and not Save/picker/share verification.
- Static linkage: 109 boundary classes resolve; Trace present. Lint NOT RUN (same offline exclusions as vc8).

## What Owner should test this round
- Nothing: HELD, no user handoff. This worker submits nothing.

## Acceptance/test IDs covered
- M06/C07 setup and output geometry remain as in vc8 and are confirmed on S23. Native decode-gate A/V sync is fixed at the product encode/mux path and passed on a local software emulator and on an official S23 selected-method run, with thresholds unchanged.

## Launch and ops handoff review
- [x] Actual app/test APK forbidden-permission gate passed: no READ_MEDIA_IMAGES/VIDEO/AUDIO, broad READ/WRITE/MANAGE_EXTERNAL_STORAGE, INTERNET or requestLegacyExternalStorage.
- [x] App permission only own DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; test additionally REORDER_TASKS. Signing/package/version/ABI/target verified. No fixture assets in app.
- [x] No new SDK/analytics/network features; runner boundary 109 classes intact.
- [x] Official S23 selected-method native decode gate passed on this pair (synthetic fixtures; thresholds unchanged).
- [ ] Save/picker/share, and human A/V acceptance: still pending. No personal media/URIs/device identifiers in evidence.

## Root cause / regression evidence
- S23 and goldfish output files had no `elst`/`iTunSMPB`, audio PTS starting at 0, and ~2048 silent decoded samples before real content. Official lag 40 ms / unmarked 0.782 / marked 1.452 is that offset. Writing only an edit list would leave players that ignore edit lists late. The product encoder wrapper flushes the delayed tail and omits priming from the muxed timeline; output delay/padding stay 0. Source assets with nonzero delay/padding are still rejected.
- Local passing evidence under `<PROJECT_ROOT>/reports/bug003-emu/run2/evidence/` (synthetic fixtures only). Build evidence under `<PROJECT_ROOT>/reports/vc9/`. Official S23 confirmation is recorded only as a generic selected-method PASS; raw result artifacts are not published here.

## Reproduction (local only)
- Environment ANDROID_HOME=<PROJECT_ROOT>/.tools/android-sdk; JAVA_HOME=<PROJECT_ROOT>/.tools/jdk-17; prepend JDK bin.
- From app/android, after clean (do not pass x86_64): `./gradlew :app:clean :app:assembleRelease :app:assembleReleaseAndroidTest -Peventclip.entry=product -Peventclip.abi=arm64-v8a --offline --no-daemon -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`.
- Essential audit: `python3 scripts/audit-vc6-product.py reports/vc9/EventClipSpike-offline-arm64-vc9-v1.5.1-private-HELD.apk reports/vc9/EventClipSpike-arm64-vc9-release-androidTest-HELD.apk vc9`.
- Linkage: `python3 scripts/audit-instrumentation-linkage.py reports/vc9/EventClipSpike-offline-arm64-vc9-v1.5.1-private-HELD.apk reports/vc9/EventClipSpike-arm64-vc9-release-androidTest-HELD.apk --android-jar .tools/android-sdk/platforms/android-37.0/android.jar --boundary reports/vc7/runner-boundary.json --out reports/vc9/runner-linkage.json`.
- Selected method unchanged: `com.eventclip.spike.ProductM06InstrumentedTest#productTwoBus720p_actualDecodeGate`. Official 120s/160s defaults unchanged; local emulator may pass `-e exportTimeoutMs` only.

## Bugs fixed
- BUG-003 unsignaled AAC priming / A/V sync offset fixed in HELD vc9 and verified on a local software emulator and on an official S23 selected-method run (lag 0 ms, thresholds unchanged). BUG-002 filesDir alias and portrait geometry remain as in vc8 and were already confirmed on S23. Watchdog and gapless source guard unchanged.

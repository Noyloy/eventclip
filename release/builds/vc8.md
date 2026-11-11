> Latest public status: After the packaging fix, a later S23 execution started the selected case and failed in BirthdayProjectStore construction (BUG-002). That constructor failure is a product files-directory alias check (official Android filesDir/cacheDir symlink vs a lexical-equals-canonical require). It is fixed in HELD vc8, together with a follow-on Media3 default that encoded a portrait composition as landscape plus rotation 90, which the output audit correctly rejected. A later official S23 run of vc8 confirmed setup and EXPORT_READY at 720×1280 rotation 0, then failed the official native decode-gate thresholds with about 40 ms of unsignaled AAC delay (BUG-003, same numbers as a local software emulator). That lag is fixed in HELD vc9. Product APKs remain HELD. No raw private execution identifiers are published. Historical entries below describe their preparation-time evidence, not Owner listening or visual acceptance.

# EventClip build handoff: vc8 — HELD / runtime UNVALIDATED

- Version code 8; version 1.5.1-private; package com.eventclip.spike; arm64-v8a.
- Date: 2026-11-11 12:52 Asia/Jerusalem; built locally/offline by Engineer.
- App: `<PROJECT_ROOT>/reports/vc8/EventClipSpike-offline-arm64-vc8-v1.5.1-private-HELD.apk`
- SHA256 `1180aa096c44a781eee3d8ffcd578aaefdbc7a1ae96375448b3dc06d9cde8829`; 13,347,743 bytes (<24,000,000).
- Matching RELEASE test: `<PROJECT_ROOT>/reports/vc8/EventClipSpike-arm64-vc8-release-androidTest-HELD.apk`
- SHA256 `bd6d0dfb9d2cf87d165b7a9a97a07a185819c9444c88b2fef66f7f3793941d5e`; 2,952,455 bytes.
- Both verified certificate SHA256 `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c` matches held vc5/vc6/vc7.
- **HELD: do NOT hand to Owner/install/uninstall/clear data/upload/submit.** Parent owns any separately authorized S23/Test Lab run. Use both vc8 files together, not vc7. Prefer vc9 for the priming fix.

## Changelog (two lines max)
- BUG-002: settle official filesDir/cacheDir alias to a canonical package-private root, then keep strict child/symlink/`..` guards. Media3 portrait encoding enabled so output is 720×1280 rotation 0.
- Ownership/symlink safety, 21000 ms mux watchdog, gapless guard, common postmix 0.5, and runner-boundary rules not weakened. Diagnostic default remains vc5.

## Known issues
- Official S23 selected-method run of vc8: setup and export audit passed (720×1280 rotation 0, EXPORT_READY), then missed official decode-gate lag/amplitude windows by about 40 ms of unsignaled AAC delay (BUG-003). Thresholds were not relaxed. Fixed in vc9.
- A prior local software-emulator run showed the same 40 ms lag. That emulator result is not itself S23 evidence.
- Static linkage: 109 boundary classes and test-method references resolve; Trace present. Does not verify Android loader/reflection. No product Save/Share or listening/visual acceptance.
- Same offline lint exclusions as vc7, lint NOT RUN.

## What Owner should test this round
- Nothing: HELD, no user handoff. Prefer the later vc9 pair. This worker submits nothing.

## Acceptance/test IDs covered
- M06/C07 setup constructor path fixed at product level; output geometry contract enforced (not loosened). Official S23 export/geometry later confirmed; official runtime decode gate failed on this pair (see vc9).

## Launch and ops handoff review
- [x] Actual app/test APK forbidden-permission gate passed: no READ_MEDIA_IMAGES/VIDEO/AUDIO, broad READ/WRITE/MANAGE_EXTERNAL_STORAGE, INTERNET or requestLegacyExternalStorage.
- [x] App permission only own DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; test additionally REORDER_TASKS. Signing/package/version/ABI/target/synthetic-asset placement verified. No fixture assets in app.
- [x] No new SDK/analytics/network features; known SDK-prefix audit passed, not a security certification. Runner boundary 109 classes intact.
- [ ] Private Save/picker/share/M08 wording/frequency/runtime behavior and human A/V acceptance: still pending. Official selected-method decode gate failed on this pair (BUG-003). No personal media/URIs/device identifiers in evidence.

## Root cause / regression evidence
- Official Android filesDir/cacheDir is typically an alias (`/data/user/0` → `/data/data`). `File.absoluteFile.normalize()` does not resolve it; `canonicalFile` does. The store constructor required those to be equal, so construction failed on device and in instrumented staging. Fix settles the official alias, then keeps strict later checks. JVM symlink fixture reproduces the old failure and now constructs, persists, and still rejects `..` and escaping symlinks.
- Media3 default portrait encoding writes 1280×720 plus rotation 90 for a 720×1280 composition. Output audit correctly rejected that. Portrait encoding is now enabled; validate still requires 720×1280 rotation 0.
- Evidence under `<PROJECT_ROOT>/reports/vc8/` and `<PROJECT_ROOT>/reports/bug002-emu/` (synthetic fixtures only).

## Reproduction (local only)
- Environment ANDROID_HOME=<PROJECT_ROOT>/.tools/android-sdk; JAVA_HOME=<PROJECT_ROOT>/.tools/jdk-17; prepend JDK bin.
- From app/android, after clean (do not pass x86_64): `./gradlew :app:clean :app:assembleRelease :app:assembleReleaseAndroidTest -Peventclip.entry=product -Peventclip.abi=arm64-v8a --offline --no-daemon -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`.
- Essential audit: `python3 scripts/audit-vc6-product.py reports/vc8/EventClipSpike-offline-arm64-vc8-v1.5.1-private-HELD.apk reports/vc8/EventClipSpike-arm64-vc8-release-androidTest-HELD.apk vc8`.
- Linkage: `python3 scripts/audit-instrumentation-linkage.py reports/vc8/EventClipSpike-offline-arm64-vc8-v1.5.1-private-HELD.apk reports/vc8/EventClipSpike-arm64-vc8-release-androidTest-HELD.apk --android-jar .tools/android-sdk/platforms/android-37.0/android.jar --boundary reports/vc7/runner-boundary.json --out reports/vc8/runner-linkage.json`.
- Runner androidx.test.runner.AndroidJUnitRunner targets the SAME minified release package. Selected method unchanged: `com.eventclip.spike.ProductM06InstrumentedTest#productTwoBus720p_actualDecodeGate`.

## Bugs fixed
- BUG-002 constructor/setup failure and follow-on output geometry (landscape plus rotation 90) fixed in HELD vc8 and later confirmed on S23 (EXPORT_READY, 720×1280 rotation 0). Official decode gate then failed with a 40 ms unsignaled AAC lag (BUG-003), addressed in vc9. Watchdog and gapless guard unchanged.

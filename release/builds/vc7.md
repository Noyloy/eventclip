> Latest public status: The initial S23 execution failed before any case because the runner could not load a shared dependency. After the packaging fix, a second S23 execution started one test case, then failed in BirthdayProjectStore construction before product rendering. This confirms runner startup progressed on-device; the constructor failure root cause is not established here. Product audio/video acceptance remains unproved. No raw result artifacts or private execution identifiers are published. Historical entries below describe their preparation-time evidence, not a later successful render.

# EventClip build handoff: vc7 r2 — HELD / runtime UNVALIDATED

- Version code7; version1.5.1-private; package com.eventclip.spike; arm64-v8a.
- Date: 2026-11-07 10:12 Asia/Jerusalem; built locally/offline by Engineer.
- FINAL app: `<PROJECT_ROOT>/reports/vc7/EventClipSpike-offline-arm64-vc7-v1.5.1-private-r2-HELD.apk`
- SHA256 `5a91198fb0e4fa460b44a2242eb62c8acb8a7a22875047cab662b07721b723b6`; 13,347,619 bytes (<24,000,000).
- FINAL matching RELEASE test: `<PROJECT_ROOT>/reports/vc7/EventClipSpike-arm64-vc7-release-androidTest-r2-HELD.apk`
- SHA256 `337193a347f3056e6cdf67fe349a0972daef2d58339e8e306c8b17f6fb030456`; 2,951,151 bytes.
- Both verified certificate SHA256 `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c` matches held vc5/vc6.
- **HELD: do NOT hand to Owner/install/uninstall/clear data/upload/submit.** Parent owns any separately authorized single S23 rerun. Original vc6 immutable; candidate1 is superseded, NOT for rerun. Use both FINAL r2 files together, not vc6 target or candidate1.

## Changelog (two lines max)
- BUG-001 runner dependency/shared-minification ABI fix; exact109 class/member boundary kept, full app minification retained.
- No product feature/source behavior changes; embedded JS identical to vc6; diagnostic default remains vc5, watchdog21000ms and gapless guard unchanged.

## Known issues
- Actual REDACTED_MATRIX_ID startup NoClassDefFoundError androidx.tracing.Trace, secondary ANR, zero cases. This pair addresses packaging statically, NOT measured Android startup success.
- Static type and128 boundary-method references resolve; no missing Kotlin/test/tracing/concurrent types. Does not verify Android loader/reflection or optional platform APIs. No product native/audio/Save/Share acceptance.
- Same offline lint exclusions as vc6, lint NOT RUN; no dependencies downloaded. No repeated unrelated JVM/Node/TS runs; previous84/12/TS unchanged.

## What Owner should test this round
- Nothing: HELD, no user handoff. Parent may separately authorize at most ONE S23 selected-method rerun, outer180s; this worker submits nothing.

## Acceptance/test IDs covered
- M06/C07,C08 startup prerequisite only; zero product acceptance. M09/M10 artifact safety checks static; all canonical runtime gates pending.

## Launch and ops handoff review
- [x] Actual app/test APK forbidden-permission gate passed: no READ_MEDIA_IMAGES/VIDEO/AUDIO, broad READ/WRITE/MANAGE_EXTERNAL_STORAGE, INTERNET or requestLegacyExternalStorage.
- [x] App permission only own DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; test additionally REORDER_TASKS. Backup exclusions/provider scope inspected in actual optimized APK. Signing/package/version/ABI/target/test classes/synthetic assets verified. No fixture assets in app.
- [x] No new SDK/analytics/network features; cached dependency graph only; known SDK-prefix audit passed, not a security certification.
- [ ] Private Save/picker/share/M08 wording/frequency/runtime behavior: previously wired, still pending platform verification. No personal media/URIs/device identifiers in evidence.

## Root cause / regression evidence
- Cached app/test dependency graphs include androidx.tracing1.2.0; original target R8 mapping shows Trace methods inlined and original class missing from BOTH APK DEX definitions. Test runner retains original Trace references. Shared dependency de-duplication assumed unshrunk target ABI; test mapping cannot resurrect removed classes/methods.
- Generator inspects nine runner-exclusive runtime artifacts and own pre-R8 instrumentation bytecode against actual app runtime class jars. Produces exact109 original class/member keeps, not package-wide library keeps or jar copying. Final DEX gate confirms all109 boundary classes and128 method references, no missing Kotlin/test/tracing/concurrent types; original vc6 fails. Optional SDK inventory retained separately, not falsely required/green.
- Evidence `<PROJECT_ROOT>/reports/vc7/runner-boundary.json`, runner-linkage-final.json, pair-provenance.json, build-final.log; reports/vc7-artifact-audit.json; reports/vc6-original-linkage.json.

## Reproduction (local only)
- Environment ANDROID_HOME=<PROJECT_ROOT>/.tools/android-sdk; JAVA_HOME=<PROJECT_ROOT>/.tools/jdk-17; prepend JDK bin.
- From app/android: `./gradlew :app:assembleRelease :app:assembleReleaseAndroidTest -Peventclip.entry=product --offline --no-daemon -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease`.
- Classpath inventory: project root `app/android/gradlew -p app/android -I <PROJECT_ROOT>/scripts/instrumentation-classpath.init.gradle :app:recordInstrumentationClasspath -Peventclip.entry=product --offline --no-daemon`; after instrumentation Kotlin compile `python3 scripts/generate-runner-boundary.py`, then rebuild pair. Source/evidence hashes record exact generated input.
- Essential audit: `python3 scripts/audit-vc6-product.py reports/vc7/EventClipSpike-offline-arm64-vc7-v1.5.1-private-r2-HELD.apk reports/vc7/EventClipSpike-arm64-vc7-release-androidTest-r2-HELD.apk vc7`.
- Linkage: `python3 scripts/audit-instrumentation-linkage.py reports/vc7/EventClipSpike-offline-arm64-vc7-v1.5.1-private-r2-HELD.apk reports/vc7/EventClipSpike-arm64-vc7-release-androidTest-r2-HELD.apk --android-jar .tools/android-sdk/platforms/android-37.0/android.jar --boundary reports/vc7/runner-boundary.json --out reports/vc7/runner-linkage-final.json`.
- Runner androidx.test.runner.AndroidJUnitRunner targets SAME minified release package com.eventclip.spike. Selected future method unchanged: class com.eventclip.spike.ProductM06InstrumentedTest#productTwoBus720p_actualDecodeGate, no full-class/1080/Robo sweep. Synthetic provenance/assets unchanged and hashed.

## Bugs fixed
- BUG-001 fixed in HELD vc7 r2 at compile/static packaging level; Android verification pending. Legacy TransformerThreadCoordinator/AppLooperGate review risks explicitly NOT repaired in this batch.

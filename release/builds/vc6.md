> Latest public status: The initial S23 execution failed before any case because the runner could not load a shared dependency. After the packaging fix, a second S23 execution started one test case, then failed in BirthdayProjectStore construction before product rendering. This confirms runner startup progressed on-device; the constructor failure root cause is not established here. Product audio/video acceptance remains unproved. No raw result artifacts or private execution identifiers are published. Historical entries below describe their preparation-time evidence, not a later successful render.

# EventClip build handoff: vc6 — HELD / runtime UNVALIDATED

- Version code:6; version name:1.5.0-private; package:com.eventclip.spike; arm64-v8a
- Date: 2026-11-07 09:44 Asia/Jerusalem
- Built by: Engineer, local offline build only
- APK path: `<PROJECT_ROOT>/reports/vc6/EventClipSpike-offline-arm64-vc6-v1.5.0-private-HELD.apk`
- SHA256: `712c5dfb9bf733c4786c800a11d94d683463f94fbe2ca0036547b2c6d7661df2`; size 13,163,553 bytes (<24,000,000)
- Signing certificate SHA256: `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`; SAME held vc5 public cert, no keystore changes.
- **DO NOT INSTALL / UNINSTALL / CLEAR DATA / UPLOAD / HAND TO Owner.** No user APK acceptance. Same package preserves legacy files on a future authorized upgrade; no migration/cleanup authorized.

## Changelog (two lines max)
- Opt-in genuine product entry with shared preview/export/delivery foundation; diagnostic selector default unchanged.
- Genuine release-target ProductExportController synthetic native test APK prepared, not executed.

## Known issues
- No Android product render/decoded MP4/clock/ExpoView/MediaStore/share/advisory/pixel/listening proof. Gapless nonzero metadata guard remains and may block fixture export; raw impulse probe does not establish trimming. Medium tones not truepeak/speech acceptance.
- Lint NOT RUN: offline library lint graph had uncached profileinstaller1.3.0/tracing1.0.0/core-ktx1.2.0; three lintVital tasks excluded. No deps downloaded. Runtime/static privacy audit separate and passed.
- Preview-first with explicit Play accepted; no autoplay/reentry/resume required. Designer121 current exact binding; no outstanding six-key copy gap.

## What Owner should test this round
- Nothing yet; HELD pending actual native gate and parent authorization. Do not install or send.

## Acceptance/test IDs covered (M01–M10, C01–C11)
- M06/C07,C08 genuine native test implementation only, no runtime PASS. M04/C01,C02 static geometry; M08/C09 package/delivery static; M09/C04,C10 safety foundation; M10/C11 copy binding. Canonical runtime acceptance remains pending.

## Launch and ops handoff review
- [x] Actual app AND test APK permission audit: no READ_MEDIA_IMAGES/VIDEO/AUDIO; READ/WRITE_EXTERNAL_STORAGE; MANAGE_EXTERNAL_STORAGE; INTERNET; requestLegacyExternalStorage. App declares only own DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION.
- [x] Actual application backup flags/exclusions, scoped FileProvider XML audited from optimized resources; product Hermes marker/Metro root/native class presence verified; generated autolink registration inspected (runtime availability pending); test assets absent from app; test classes/synthetic assets present in test APK.
- [x] Known analytics/crash/ad package-prefix audit absent and cached release runtime graph recorded. No new SDK/network features; not a universal security certification.
- [ ] Picker/MediaStore/M08 advisory/Save/Share runtime acceptance — code wired, unexecuted. Songs ACTION_OPEN_DOCUMENT, no audio broad permission.
- [x] Note contains no personal media/source URIs/device identifiers. All fixtures local generated synthetic; random isolated test storage never touches current project or legacy outputs.

## Matching release instrumentation pair and future gate (NOT SUBMITTED)
- Test APK `<PROJECT_ROOT>/reports/vc6/EventClipSpike-arm64-vc6-release-androidTest-HELD.apk`; SHA256 `34d758897cf9053d38fd12d0fb6805ee59ce150c683f104125b6fe33abba091b`; bytes 2,951,131; same cert above.
- Runner androidx.test.runner.AndroidJUnitRunner; targetPackage com.eventclip.spike. Built by :app:assembleReleaseAndroidTest against SAME minified release, not debug fallback. Test target is the held app hash above.
- FIRST future selection ONLY `class com.eventclip.spike.ProductM06InstrumentedTest#productTwoBus720p_actualDecodeGate`, one supported environment, outer cap180s (internal160s/test170s), no full300/1080 sweep.
- Later separate methods: productNonzeroSourcePts_actualDecodeGate; gaplessImpulseRawProbe_guardRemains; sharedCompositionPlayer_surfaceAndRealClockSmoke. Class com.eventclip.spike.ProductM06InstrumentedTest.
- Evidence (future runtime): target externalFilesDir/product-m06-evidence/<random-run-id>, MP4+JSON+native f32 PCM/metrics; retain/pull only synthetic evidence with separate authorization. Host `python3 scripts/inspect-product-m06-host.py <evidence-dir>` independently verifies outputSHA/FFmpeg PCM. Nothing executed yet.
- Fixture provenance `reports/product-m06-synthetic/provenance.json` and gapless-provenance.json; exact embedded test assets and source hashes `reports/vc6-source-fixture-manifest.json`. Nonzero guard never weakened by host skip metadata.

## Reproduction / exact local gates
- Environment ANDROID_HOME=<PROJECT_ROOT>/.tools/android-sdk; JAVA_HOME=<PROJECT_ROOT>/.tools/jdk-17; JDK bin first in PATH.
- From app/android: `./gradlew :eventclip-render:testDebugUnitTest :app:assembleRelease :app:assembleReleaseAndroidTest -Peventclip.entry=product --offline --no-daemon -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease` (JVM84 PASS; final pair build PASS).
- From app: `node scripts-test-product-flow.cjs` (12 PASS); `./node_modules/.bin/tsc --noEmit` PASS.
- From project root: `python3 scripts/audit-vc6-product.py reports/vc6/EventClipSpike-offline-arm64-vc6-v1.5.0-private-HELD.apk reports/vc6/EventClipSpike-arm64-vc6-release-androidTest-HELD.apk` PASS. Exact JSON/logs in reports/vc6-*; static not Android runtime.
- Default/explicit `-Peventclip.entry=diagnostic` retains diagnostic entry/vc5 version scheme. Historical held vc5 artifact unchanged; no promise evolved native sources rebuild identical old bytes.

## Bugs fixed from the bug log
- No runtime bug closure claimed. Preparation/build-only fixes: release instrumentation optional annotation R8 rules, optimized resource-path audit. Existing gapless/runtime gates remain open.

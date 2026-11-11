# EventClip — Android private prototype source

A local Android/Expo/Media3 prototype. This is a sanitized, fresh source snapshot, not an accepted release or a history of earlier work. No signing keys, APKs, personal media, cloud identities or Test Lab results are included. No license grant is supplied.

## Layout
- [App and native module](app/) — product and legacy diagnostic entries, typed policy/store/audio/export/preview/delivery code and tests.
- [Engineering status](docs/realmedia-foundations.md) — historical implementation evidence; NOT runtime acceptance.
- [Design handoff and EN/HE strings](design/) — narrow Android private-build handoff is authoritative for this slice, not the broader MVP below it.
- [Release notes and draft privacy/store materials](release/) — sanitized historical HELD notes, not distribution or legal approval.
- [Synthetic media allowlist](fixtures/ALLOWLIST.json) and [provenance](fixtures/synthetic-m06/provenance.json).
- [Test Lab documentation](testlab/docs/) and [templates](testlab/templates/) — sanitized procedures/static help only; no cloud operation is authorized by this repository.
- [Host harness](harness/) and [local audit scripts](scripts/).

## Build requirements (not executed for this publication snapshot)
Use Node/npm compatible with app/package.json, JDK17, Android SDK/NDK and Gradle versions declared under app/android. Install dependencies with `cd app && npm ci`; set your local ANDROID_HOME/JAVA_HOME (or an ignored local.properties). The small Gradle wrapper is included; dependencies/SDKs are not vendored. A clean machine may need downloads; no offline-build guarantee is made.

From app/android: `./gradlew :app:assembleRelease :app:assembleReleaseAndroidTest -Peventclip.entry=product`. Product currently uses vc9/1.5.1-private and release minification. Diagnostic remains default; use `-Peventclip.entry=diagnostic` explicitly when intended. Legacy diagnostic binary seed packs are intentionally excluded: that flow is not turnkey here. The legacy host beat corpus is likewise excluded; only manifest-verified short product fixtures are supplied.

Release signing is local-only: provide EVENTCLIP_KEYSTORE_PATH, EVENTCLIP_STORE_PASSWORD, EVENTCLIP_KEY_ALIAS and EVENTCLIP_KEY_PASSWORD securely in your environment, or produce an unsigned release. No private signing identity can be reproduced from this snapshot. Never uninstall/clear an existing private installation to replace its signing identity. Release instrumentation requires the matching signed app/test pair.

The runner-boundary ProGuard rules are checked in. To regenerate them, first compile release instrumentation, run scripts/instrumentation-classpath.init.gradle via the Gradle inventory task, then scripts/generate-runner-boundary.py and rebuild. Historical artifact-audit scripts require locally supplied HELD baseline APKs excluded from this snapshot; do not mistake missing historical artifacts for a build result. Use fresh local outputs and read each script before invoking it. No scripts have been run from this snapshot.

## Current limitations
All APKs remain HELD; source publication does not authorize distribution or test execution. The initial S23 execution failed before any case because the runner could not load a shared dependency. After the packaging fix, a later S23 execution started one test case, then failed in BirthdayProjectStore construction (BUG-002: official filesDir/cacheDir alias vs a lexical-equals-canonical require). That setup failure and a follow-on portrait-geometry default are fixed in HELD vc8 and confirmed on S23 (EXPORT_READY, 720×1280 rotation 0). The official native decode gate on vc8 then failed with a 40 ms audio lag from unsignaled AAC-LC encoder priming (BUG-003). HELD vc9 trims that priming from the muxed timeline. A later official S23 selected-method run of vc9 passed the same unchanged decode-gate thresholds (lag 0 ms, original-voice ratios about 1.00, all windows in range). This is not listening, visual, Save, or Share acceptance. No raw result artifacts or private execution identifiers are published. Metadata audits do not prove decoded correctness. Gapless guards and the 21,000ms mux watchdog remain unchanged. Do not present the prototype as complete M01–M10 functionality.

Cloud templates use YOUR_FIREBASE_PROJECT_ID and reviewed local APK paths; replace them only after separate account, privacy, budget and execution authorization. All internal links/local evidence paths are redacted. Draft policies need product/legal review. No credentials should ever be committed.

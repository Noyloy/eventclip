> Current generic status: HELD vc9 is the current product APK pair. Official S23 selected-method instrumentation reached EXPORT_READY at 720×1280 rotation 0 and passed the unchanged native decode-gate thresholds (lag 0 ms, original-voice ratios about 1.007 / 0.993, final 0.996, correlation about 0.99983, all windows in range). That closes BUG-002 (filesDir alias and portrait geometry, already confirmed on S23 in vc8) and BUG-003 (unsignaled AAC-LC priming lag of 40 ms on vc8). Product APKs remain HELD. This is not listening, visual, Save, or Share acceptance. No raw result artifacts or private execution identifiers are published.

# EventClip bug log

Route every new entry to the Family video app owner for triage. User questions and build handoffs go through Owner only.
Canonical current milestone: top of the PRD, INTERNAL_LINK_REMOVED

Privacy rule: never copy real media, filenames, source URIs or raw device identifiers into a record. Describe the content generically (for example "12 photos + 3 portrait videos, one 4-min song") and point to evidence by a privacy-safe reference only.

Statuses: new, routed, fixing, fixed in vcN, verified, won't fix.
Severity: blocker (can't make a video), major (wrong output or crash), minor, polish.
Test ID: affected acceptance/test ID, M01–M10 or C01–C11.
Evidence type: phone, emulator, or build-only.

| ID | Date | Build (vc) | Test ID | Reported by | What happened | Steps / content (generic) | Evidence type | Evidence ref (privacy-safe) | Severity | Status | Routed to | Fixed in |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
|BUG-001|2026-11-07|6|M06/C07|S23 Test Lab evidence|Runner startup NoClassDefFoundError androidx.tracing.Trace; secondary ANR; ZERO test cases, no product output|Run selected synthetic product instrumentation method|phone (S23 Test Lab)|REDACTED_MATRIX_ID/RESULT.md; original APK DEX closure and R8 maps|blocker|runner startup verified on device after packaging fix; render unverified|Engineer via Owner|vc7 packaging; case reached on device; separate setup failure is BUG-002|
|BUG-002|2026-11-07|7|M06/C07|Generic subsequent S23 execution|One case started; BirthdayProjectStore constructor failed because official filesDir/cacheDir alias (`/data/user/0` vs `/data/data`) did not pass a lexical-equals-canonical require|Selected synthetic product test|phone (S23 Test Lab) plus local JVM symlink fixture and software emulator|REDACTED_MATRIX_ID/RESULT.md; local JVM alias tests; local emulator retained synthetic evidence|blocker|fixed in vc8 (setup/constructor and portrait output geometry); confirmed on later S23 export|Engineering|vc8|
|BUG-003|2026-11-11|8|M06/C07|S23 Test Lab evidence|Export reached READY at 720×1280 rotation 0, but the official native decode gate failed with 40 ms envelope lag and original-voice amplitude ratios 0.782 / 1.452; music and peak windows were in range. Same numbers as the software emulator.|Selected synthetic product test|phone (S23 Test Lab) plus host probe of the product MP4 and local emulator|REDACTED_MATRIX_ID/RESULT.md; host ffprobe (no elst / iTunSMPB, audio PTS 0, 2048-sample silence); local passing evidence after the fix; later official S23 vc9 result|blocker|fixed in vc9 (priming trimmed from muxed timeline); confirmed on later official S23 selected-method run|Engineering|vc9|

BUG-001 update: runner startup progressed on-device after the packaging fix. This is not verification of product rendering or audio.
BUG-002 update: confirmed fixed on a later S23 execution (setup OK, EXPORT_READY, 720×1280 rotation 0, container audit PASS).
BUG-003 update: root cause is unsignaled AAC-LC encoder priming (~2048 samples / two frames) muxed as content at PTS 0 with no edit list. The product encoder wrapper pads silent PCM through EOS so the delayed tail is flushed, drops the priming frames, and rebases PTS to 0. Output delay/padding stay 0; source gapless rejection of nonzero delay/padding is unchanged. A local software emulator then passed the official decode-gate thresholds (lag 0 ms, voice ratios about 1.00). A later official S23 selected-method run of HELD vc9 also passed the same unchanged thresholds (lag 0 ms, unmarked 1.007, marked 0.993, final 0.996, correlation 0.99983, all windows in range). This is not listening or visual acceptance.

> Current generic status: The initial S23 execution failed before any case because the runner could not load a shared dependency. After the packaging fix, a second S23 execution started one test case, then failed in BirthdayProjectStore construction before product rendering. This confirms runner startup progressed on-device; the constructor failure root cause is not established here. Product audio/video acceptance remains unproved. No raw result artifacts or private execution identifiers are published.

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
|BUG-002|2026-11-07|7|M06/C07|Generic subsequent S23 execution|One case started; BirthdayProjectStore constructor failure before product render; root cause not established|Selected synthetic product test|device evidence summarized, raw results excluded|Private execution identifiers omitted|blocker|open, diagnosis pending|Engineering|not fixed|

BUG-001 update: runner startup progressed on-device after the packaging fix. This is not verification of product rendering or audio. BUG-002 is separate and has no established root cause in this public snapshot.

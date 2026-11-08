# EventClip — Android private real-content build: design handoff (S23 Ultra)

Status: DESIGN ARTIFACT for Engineer + app owner, prepared Sat Nov 7, 2026 (Asia/Jerusalem) at Owner's request. Proposed design only, not device acceptance. No tests were run by Designer. Canonical acceptance = PRD M01–M10 / C01–C11 (INTERNAL_LINK_REMOVED). This file adds no second checklist; the "Evidence" column only says what the existing IDs need to see.
Strings: `eventclip-android-strings-en-he.json` (same folder), keyed to Engineer's import/export state codes.

## 1. Slice scope (current brief from Owner / owner / Engineer)
- Goal: real photos/videos picked on the device → one local DRM-free song → Birthday create → preview ready (explicit Play) → Save to phone (720p MP4) → music/sharing info → Android share sheet. No sample substitution, no uploads, no cloud processing.
- IN: Birthday only; 720p only; EN + HE RTL; system Photo Picker + document picker; private app-storage copies; manual include/exclude whole items; chronological order (on, reversible); optional manual voice marks + skip-all; Save and Share as separate actions.
Cloud/account-specific operational detail omitted; perform independent authorized preflight.
- Watermark (private 720p slice, owner): exact spec §11; provisional small bottom-right corner "Made with EventClip", no paywall/removal control. Provisional handoff only — not public/free-tier branding or founder approval. Current build watermark is unverified: Engineer must compare and report any difference BEFORE changing it.
- Project (owner-settled): ONE current editable project, no list. "Start a new movie" → confirm Keep current / Replace project. Replacement is atomic: confirmation authorizes replacing only app-owned source copies, but the current project stays intact until new imports validate and commit; cancel/fail keeps it; temporary headroom required; no cleanup racing replacement. Saved/shared movies are never deleted; the last good private MP4 stays until the user separately deletes it.
- Caps (Engineer-aligned, NOT measured capacity): see §5.
- Proposed media baseline (Engineer contract, proposed target, not accepted): JPEG/PNG SDR; MP4 8-bit SDR H.264, AAC audio optional; songs DRM-free MP3, AAC/M4A, PCM WAV. Decide by probing/decoders, not extension. HEIC/HEVC/HDR/other → UNSUPPORTED_MEDIA, no conversion.

## 2. Arrangement rules surfaced in UI (Engineer contract)
- Whole clips, no trim. Photos target 4 s; snap to an outgoing beat within 3–5 s, else 4 s. Song starts at 0. No loops, omissions or speed changes.
- Arranged duration is shown BEFORE Create (READY_TO_CREATE).
- Song shorter than arranged movie → SONG_TOO_SHORT blocks Create: "Choose a longer song" / "Remove items".
- Song longer → disclosed before Create: music fades over the last 1 s, ending at the movie end; original clip audio (voices) preserved.
- Birthday defaults (owner-selected): warm upbeat; music ducks to ~15% with ~0.5 s fades ONLY in user-marked ranges; numbers never shown in UI.

## 3. Screens (12; 3 are system handoffs)
| ID | Screen | Key states / actions | Maps to |
|---|---|---|---|
| A01 | Start Birthday | New: "Choose photos and videos" (no permission prompt before tap). If a current project exists: card "Continue your movie" (reopens private media, order, exclusions, marks, song; works offline/after source revocation) + "Start a new movie" with safe replace confirm (default = keep). one current project, no list (owner-settled) | M02, M03, C04, C05 |
| A02 | System Photo Picker (OS) | Opened on tap (point of use). Cancel → back to A01/A03 unchanged; no READ_MEDIA_IMAGES/VIDEO | M02, C04 |
| A03 | Import progress | COPYING {done/total}, CHECKING, READY; Stop → IMPORT_CANCELLED (delete only incomplete copies) | M02, C04, C05 |
| A04 | Selected media review | Per-item In/Out switch (48dp, TalkBack state), counts in/out, Undo, Keep time order switch (on), size "x of 1 GiB incl. song"; In-the-movie switch (exclude) vs separate "Remove from this movie project" (deletes private copy only, never Gallery original), Add more; per-item errors inline with actions | M01, M05, C01, C06 |
| A05 | Song intro | Line "Only music files saved on this phone…" then Browse | M02, C03 |
| A06 | System document picker (OS) | Audio types only; cancel keeps prior song | M02, C03, C04 |
| A07 | Song check (replacement atomic: old song kept until new one commits; needs temp headroom) | CHECKING → READY or PROTECTED_AUDIO / UNSUPPORTED_MEDIA / SONG_TOO_SHORT with recovery | C03, M01 |
| A08 | Keep voices (optional) | Per clip "Keep this voice" → Mark start / Mark end on playback, list of marks, Remove, Next clip; primary CTA by saved valid ranges: 0 → "Continue without marking voices", ≥1 → "Continue with saved marks" (ranges keep softening music; adds none). Only per-range Remove changes saved ranges; no hidden skip preference. Pending start without end: inline prompt "Mark the end" / "Discard this start" (discards only the pending start; never saved as a range; no new screen). Footer (helper "Clip sound stays on. Music won't soften unless you mark a section."); on re-entry skip-all adds no NEW marks and keeps existing ones (no reset). All included clips keep original sound, marked or not; marks soften MUSIC only; no auto speech detection, normalization or muting | M06, C07 |
| A09 | Ready to create | PLANNING → READY_TO_CREATE: movie length, long-song fade disclosure (music-only fade; clips keep their own sound), style line (marks / no marks), foreground warning, "Create movie (720p)"; caps errors block here | M01, C01, C08 |
| A10 | Creating | EXPORTING truthful progress; Cancel → CANCEL_REQUESTED → EXPORT_CANCELLED; leaving app → EXPORT_INTERRUPTED; errors EXPORT_FAILED / storage | M09, C10 |
| A11 | Movie ready | EXPORT_READY: created file shown ready for review first with prominent Play (no auto-start); "file is a PRIVATE export ("Ready in EventClip. Not saved to your phone yet."); "Save to phone" and "Share…" separate; save.SAVING → save.SAVED "Saved to Movies › EventClip" only after MediaStore commit, else SAVE_FAILED; "Make changes" back to A04; Music & sharing panel may appear after successful export, before save (once per file) | M08, C09 |
| A12 | Android share sheet (OS) | First Share per file: inline "Continue to share" in panel → sheet. Later shares: straight to sheet. Dismiss keeps file | M08, C09 |
Restart screen = A10 terminal states return to A09 with explicit "Start again" (never Resume) and "Your previous movie is still saved" when a prior EXPORT_READY file exists.

## 4. State → string keys (exact Engineer codes)
Import: COPYING, CHECKING, READY, IMPORT_CANCELLED, ACCESS_UNREADABLE (Choose again / Remove), UNSUPPORTED_MEDIA (Choose another), PROTECTED_AUDIO (Choose another), COPY_FAILED (Try again / Remove), STORAGE_LOW (free space / Try again), ITEM_LIMIT / SOURCE_SIZE_LIMIT (Remove items / smaller file), DURATION_LIMIT (Remove items), SONG_TOO_SHORT (Longer song / Remove items). All preserve prior READY items and project.
Export: PLANNING, READY_TO_CREATE, EXPORTING, CANCEL_REQUESTED, EXPORT_CANCELLED, EXPORT_INTERRUPTED, EXPORT_FAILED, EXPORT_READY. Cancel/interrupt/fail → project kept, explicit Restart, prior export file kept.
Keys: `import.<CODE>`, `export.<CODE>[.part]` in the JSON.
`save.SAVING`, `save.SAVED`, `save.SAVE_FAILED`, `export.STORAGE_LOW`: aligned with Engineer Nov 7.

## 5. Caps, fit and save (Engineer alignment Nov 7 — not device evidence)
- Items: 30 RETAINED visual items, excluded ones count; 31st → ITEM_LIMIT.
- Size: 1 GiB (1,024 MiB = 1,073,741,824 bytes) of ALL committed private source copies, incl. excluded items and current song. Excluding does not free space; "Remove from this movie project" frees only the app-owned copy, never the Gallery/Files original. Thumbnails, exports, temp files are outside the cap but need free-space headroom (STORAGE_LOW).
- Duration: ≤ 90,000,000 µs passes, > blocks (DURATION_LIMIT). Displayed seconds round UP near the cap so 90.001 s never shows as 90 s. Song-length check uses the same plan with decoded song duration.
- Song replacement atomic: current song kept until replacement commits; temporary headroom required.
- Fit: entire frame, letterbox, rotation honored; no crop controls (accepted by Engineer; was design recommendation).
- Save: MediaStore Movies/EventClip (API 29+), no broad storage permission; "Saved" only after destination commit succeeds. Deleting the private project never deletes public saved copies.

## 6. Music & sharing (owner-settled for private slice, M08)
- Save/export is never blocked; no checkbox, consent or license claim; no extra screen.
- Nonblocking info panel on A11, shown ONCE PER EXPORTED FILE (Got it dismisses).
- First Share per file: inline "Continue to share" → native sheet. Later shares of that file: no interstitial.
- Persistent "Music and sharing" link next to Share.
- EN (owner): "Songs you add may be copyrighted. Having a song file does not give permission to publish it in a video. Public posts may be muted, blocked or removed." HE adaptation in JSON. Final legal copy pending counsel / public-launch approval.
- Delta vs earlier design: this replaces the every-share pre-share gate (E08) for the private slice only. Scout's non-blocking "once before first export" note was not adopted because the owner settled placement on A11, once per exported file. Canonical M08 status unchanged.

## 7. Android / accessibility / bidi rules
- Permissions at point of use; Photo Picker for media, document picker ONLY for songs (no READ_MEDIA_AUDIO); no broad storage permission.
- Persistent access: after READY the project must not depend on picker URIs (C05). Implementation note only; no guarantee about URI lifetime.
- Save destination label: "Movies › EventClip" (aligned); no filesystem paths in UI.
- 48dp targets; every control has a TalkBack label + state; system Back preserves selections/marks/project and never discards silently.
- EN/HE semantic mirroring; video frames, scrubbing and playback direction NOT mirrored.
- Placeholders wrapped in FSI/PDI (BidiFormatter); Hebrew prefixes (ב/כ/ל) outside the isolate; dates/times/sizes via device locale (Hebrew month names in he); filenames/timecodes LTR.
- Diagnostics omit filenames, URIs, thumbnails, audio.

## 8. Coverage map to canonical IDs (design coverage ≠ implementation pass)
| ID | Design coverage | Evidence needed (Engineer/phone test) | Gaps |
|---|---|---|---|
| M01 | A04/A07/A09 caps + duration + fade disclosure | Screens at 30/31 retained, 1 GiB boundary, 90.000/90.001 s, short/long song | None (aligned §5) |
| M02 | A01–A03, A05–A07 | Picker cancel/deny, copy states on S23 | — |
| M03 | A01 Continue/Start new + replace confirm; reopen restores media, order, exclusions, marks, song | Kill app, airplane mode, revoke source, reopen | None (owner-settled) |
| M04 | Entire-frame letterbox, rotation honored (§5) | Mixed orientation output | None (aligned) |
| M05 | A04 switches, Undo, order switch | Excluded absent in output | — |
| M06 | A08 marks soften music only, skip-all helper, re-entry keeps marks; A09 style line | Listen: unmarked = normal music + clip sound; marked = softer music; end fade music only | — |
| M07 | None (listening review) | Owner listening via Owner | Not a design item |
| M08 | A11/A12, private export vs saved copy, Save vs Share | MP4 in Movies/EventClip plays; share sheet opens; cancel keeps file | None |
| M09 | A10 terminal states, restart | Cancel/background/low storage on device | — |
| M10 | Strings JSON EN/HE, bidi rules | C11 run on device | HE copy needs native review |
| C01–C11 | As cited above | Per PRD matrix | Watermark provisional; build comparison pending |

## 9. Patterns folded from Scout desk research (findings, not copied UI)
Cloud/account-specific operational detail omitted; perform independent authorized preflight.

## 10. Limits of this handoff
No device or visual testing by Designer; caps are product caps, not measured capacity; codec baseline is Engineer's proposal; watermark provisional and unverified in build; HE strings need native/legal review; build ETA belongs to Engineer.

## 10b. Engineer-reported build status + audio mix (Nov 7; not acceptance)
- Save/Share/advisory (Engineer-reported update): WIRED IN CODE (84 JVM / 12 Node + TS / Kotlin checks pass per report); NOT Android-tested; no user APK. Create still disabled unless Engineer confirms wiring.
- Preview contract (owner, private slice): finished movie is shown ready for review BEFORE Save/Share with a prominent explicit Play; clear Play/Pause; no auto-start, no autoplay setting; never auto-play on screen re-entry or app resume; saved marks/project preserved. Auto-start is NOT an acceptance requirement; full-MVP auto-play reference superseded for this slice. Playback/audio/recovery pending device. Reported build already uses explicit Play (matches).
- Recovery keys (private MP4 kept in every case): `share.failed` (couldn't OPEN sharing; no delivery claim; sheet dismiss is not an error), `share.busy` (blocks duplicate launches only while pending), `save.cancel_requested` (no completion claim before ack; racing commit may end SAVED), `save.cancelled` (confirmed only; no claim about public copy), `save.status_unknown` (no outcome claim; reconcile via MediaStore if available; Save disabled until resolved or deliberate re-save after checking), `save.unsupported` (API < 29; Share remains).
- Build status (Engineer-reported update, not acceptance): Preview + native clock WIRED IN CODE (66 JVM / 8 Node / TS / Kotlin checks pass per report), runtime unvalidated, NOT Android-tested. Create STILL DISABLED (`create.unavailable`). `playback.unavailable` / `voices.playback_required` remain fallbacks for a real unavailable failure. Lifecycle, RTL, seeking and audibility acceptance PENDING; canonical statuses unchanged. Not a ready user build.
- PROPOSED playback/marking keys (pending Engineer binding, not implemented): `playback.play`, `playback.pause`, `playback.interrupted` (preview stopped; manual Play safe; never implies export cancelled), `playback.seek_label` (adjustable slider; localized spoken durations, not colon), `playback.seek_forward` / `playback.seek_backward` ("Later/Earlier in the movie"; time-based, no fixed jump), `voices.mark_busy` (only while write outstanding), `voices.mark_failed` + `voices.mark_retry` (saved marks and valid pending start kept; no autosave/clear). Export interruption REUSES `export.EXPORT_INTERRUPTED` + `export.restart` + `export.previous_kept`. No trim/offset/timeline or preference UI.
- New keys: `import.provider_warning` (cloud-backed picks may be downloaded by Android/the file's app; EventClip uploads nothing; copies work offline after import; no claim about provider transmission). `review.move_earlier` / `review.move_later` (semantic time order, not left/right).
- Audio mix (owner M06): original bus + music bus, common fixed 0.5 output gain to avoid hard clipping; relative balance kept, absolute original loudness lower; duck and end fade on music before common gain; no normalization or new controls. Copy says "keeps its own sound", never "unchanged volume".

## 11. Provisional watermark spec (private 720p slice only)
Status: owner accepted this GEOMETRY as provisional product review (Nov 7); renderer/device verification pending; NOT public/free-tier branding, NOT founder approval. Engineer renderer support pending; current build watermark (reported 42 px, backing, default placement) unverified — Engineer compares against this spec and reports differences BEFORE changing.
- Text: "Made with EventClip" (never translated, never mirrored). Roboto Medium (wght 500) or platform sans Medium fallback; white #FFFFFF 100%.
- Backing: black rounded rect, 55% opacity (alpha 140/255), always on (conservative fallback; no per-frame detection).
- Coordinates: final output frame AFTER rotation and entire-frame fit (letterbox bars are part of the frame). Fixed physical bottom-right in all app languages.
- Formula, s = min(W,H): font px = round(0.025·s); inset = round(s·24/720) from right and bottom edges to the backing box; pad_x = round(s·8/720); pad_y = round(s·5/720); radius = round(s·6/720). Box = tight ink bbox of the text (font.getbbox) + 2·pad. Text drawn at box origin + pad − bbox offset.
- 720p constants (s = 720): font 18 px; inset 24 px (3.33%); pad 8 × 5 px; radius 6 px; measured text ink bbox 164 × 18 px (Roboto Medium, PIL); box 180 × 28 px.
  - 1280×720 landscape: box x 1076–1255, y 668–695.
  - 720×1280 portrait: box x 516–695, y 1228–1255.
- Contrast (computed, not device-measured): white on 55% black backing ≈4.7:1 worst case over pure white (backing over white = RGB 115).
- Rendering: pre-render a transparent RGBA bitmap once per output size (size from runtime Android font ink bounds; 180 × 28 is a sample only) and composite every frame; same for preview and export.
- Engineer-reported review (not acceptance): cached Media3 1.5.1 supports BitmapOverlay/OverlaySettings with existing fit-before-overlay; geometry feasible, NOT implemented or device-tested. Must convert final displayed anchors through rotation/letterbox and compute tight ink bounds with the Android font at runtime; NEVER hardcode the 180x28 sample. Existing build overlay unchanged. Preview/export parity and phone output still need validation; no pixel-match claim. Acceptance statuses unchanged.
- Synthetic examples (no private media), in `watermark/`: `SAMPLE-synthetic-watermark-landscape-1280x720.png`, `SAMPLE-synthetic-watermark-portrait-720x1280.png`, plus `-zoom4x` crops; geometry in `watermark-geometry.json`; generator `wm.py`. Visually inspected by Designer on box render only.

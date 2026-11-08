# EventClip build handoff: vc<N>

- Version code: <N>
- Version name: <x.y.z>
- Date (Asia/Jerusalem):
- Built by: Engineer (local build)
- APK path on the shared computer:
- SHA-256 of APK:
- Install: sideload on Owner's Galaxy S23 Ultra (uninstall first only if the signing key changed)

## Changelog (two lines max)
-

## Known issues
-

## What Owner should test this round
-

## Acceptance/test IDs covered (M01–M10, C01–C11)
-

## Launch and ops handoff review (fill before Owner installs)
- [ ] Merged-manifest audit of the final APK (e.g. `aapt2 dump permissions` / `apkanalyzer manifest permissions`), including permissions added by libraries:
  - [ ] no READ_MEDIA_IMAGES
  - [ ] no READ_MEDIA_VIDEO
  - [ ] no READ_MEDIA_AUDIO (songs come through the system document picker)
  - [ ] no READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE or requestLegacyExternalStorage
  - Permission list pasted here:
- [ ] Photos and videos via the system photo picker, songs via the system document picker
- [ ] No analytics, crash-reporting, ad or other data-sending SDKs, and no INTERNET-dependent features
- [ ] Copyright advisory frequency and wording match PRD M08
- [ ] Private Save works (public launch still waits on counsel approval)
- [ ] Nothing in this note contains real media, filenames, source URIs or device identifiers

## Bugs fixed from the bug log
- (BUG-### ids)

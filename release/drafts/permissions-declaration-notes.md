> **Superseded in part (Nov 7, 2026, app owner decision in the PRD's current milestone):** songs use the system document picker, with no READ_MEDIA_AUDIO. The READ_MEDIA_AUDIO justification below is obsolete. The final merged APK must not have READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO or broad/legacy storage permissions, including any added by libraries.

# Permissions notes: photo, video and audio access (DRAFT)

Sources checked on 2026-11-07:
- Details on Google Play's Photo and Video Permissions policy: https://support.google.com/googleplay/android-developer/answer/14115180
- Understanding Restricted Permissions with minimum scope alternatives: https://support.google.com/googleplay/android-developer/answer/16935362
- Permissions and APIs that Access Sensitive Information (policy text): https://support.google.com/googleplay/android-developer/answer/16558241?hl=en
- Compliance reminder (deadline history): https://support.google.com/googleplay/android-developer/answer/15800983?hl=en-GB

## Policy summary
- Apps that target Android 13+ (API 33+) "may only request the READ_MEDIA_IMAGES and READ_MEDIA_VIDEO permissions if system pickers (like the Android Photo Picker) are not sufficient" for core functionality. Apps that keep them must submit a Play Console declaration and pass review (answer/16558241).
- Apps with one-time or infrequent media use must remove those permissions and use a system picker. A custom in-app picker doesn't justify broad access (answer/14115180).
- Google's examples of eligible apps are mainly gallery and photo-manager apps. Note that "photo/video editor" is listed among the non-infrequent examples (answer/14115180), but EventClip has no need for broad access: users choose specific clips for each movie.
- Enforcement has applied to all tracks, testing included, since 22 Jan 2025 (answer/15800983).

## EventClip approach
- **Photos and videos come from the Android Photo Picker** (`ActivityResultContracts.PickMultipleVisualMedia`). No storage or media permission is needed. The app gets URI access only to the items the user picks. The picker works on Android 11+ via system updates, with a backport down to Android 4.4 via Google Play services (answer/16935362).
- **The manifest must NOT contain** `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` (for images/video), `MANAGE_EXTERNAL_STORAGE`, or `READ_MEDIA_VISUAL_USER_SELECTED`. Check the **merged** manifest, because libraries can add them. Use `tools:node="remove"` if needed.
- If the app needs a project to reopen later, call `takePersistableUriPermission` on the picked URIs, or copy them into app-private storage. This still needs no permission.
- Result: **no Photo and Video permissions declaration is needed**, which removes a review step and a likely rejection.

## READ_MEDIA_AUDIO justification (draft text for internal and review use)
`READ_MEDIA_AUDIO` isn't part of the Photo and Video Permissions policy (that policy covers only READ_MEDIA_IMAGES and READ_MEDIA_VIDEO), and as of this check there's no Play Console declaration form for it. It's a normal runtime "Music and audio" permission. Suggested justification wording, if a reviewer asks or for the listing:

> EventClip's core feature is setting a user's own photos and videos to a song they already own. The app reads the device's audio library (READ_MEDIA_AUDIO) only to list songs and let the user choose a soundtrack, and to beat-match and mix the song offline on the device. Audio files are never uploaded or shared. DRM-protected files are skipped.

Notes:
- Ask for the permission in context, when the user taps "Choose song", with an explanation screen first. If it's denied, offer a fallback with the system document picker (`ACTION_OPEN_DOCUMENT`, `audio/*`), which needs no permission.
- *Option to consider:* if the SAF document picker alone gives a good enough experience, the app could drop READ_MEDIA_AUDIO entirely. That trades a cleaner permission list for a worse song-browsing experience. It's a product decision.
- For minSdk below 33, use `READ_EXTERNAL_STORAGE` with `android:maxSdkVersion="32"` only for audio. Google's restrictions apply to apps targeting 33+, but keep the scope minimal.

> **Superseded in part (Nov 7, 2026, app owner decision in the PRD's current milestone):** songs use the document picker (no READ_MEDIA_AUDIO). Audience and content declarations wait for Owner and legal.

# Google Play Data safety form: draft answers for EventClip (DRAFT)

Sources checked on 2026-11-07:
- Provide information for Google Play's Data safety section: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- App metadata / data safety definitions: https://developer.android.com/about/versions/14/features/app-metadata

## Key rule we rely on
Google defines "collect" as *transmitting data off the user's device*, including transmission by any library or SDK. "User data accessed by your app that is only processed locally on the user's device and not sent off device does not need to be disclosed." (answer/10787469). EventClip processes photos, videos and audio only on the device, so nothing counts as collected or shared.

## Answers

### Section 1: Data collection and security
| Question | Answer | Reason |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | No data leaves the device. There are no servers, no network calls, and no analytics, crash-reporting or ad SDKs. Selecting photos, videos and songs and rendering them happens only on the device, which is out of scope per Google's on-device rule. |
| Is all of the user data collected by your app encrypted in transit? | *Not shown / N/A* | When you answer "No" above, the form skips the data-type and security questions. |
| Do you provide a way for users to request that their data be deleted? | *N/A* | Nothing is collected. Users delete projects in the app or by uninstalling. |

### Section 2: Data types
Leave **every** category unselected: Location, Personal info, Financial info, Health and fitness, Messages, Photos and videos, Audio files, Files and docs, Calendar, Contacts, App activity, Web browsing, App info and performance, Device or other IDs.
- *Photos and videos / Audio files:* accessed but **not collected**, because they're processed on the device only.
Cloud/account-specific operational detail omitted; perform independent authorized preflight.

### Section 3: Data usage and handling
Not applicable because nothing is collected.

### Expected public label
"No data collected" and "No data shared with third parties".

### Other required App content items (same Policy area)
- **Privacy policy URL:** still required. Use the hosted version of privacy-policy-en.md (it must be public before the closed test, which needs Owner's approval).
- Ads declaration: **No ads**.
- Target audience: general / 13+ (see the content rating notes in store-listing-draft.md).

## Assumptions that must stay true (otherwise update the form AND the policy before release)
- **A1. No INTERNET-based code paths in the app.** No Firebase/Crashlytics, Google Analytics, Sentry, AdMob, Facebook SDK, attribution SDKs or remote config. This is a **product requirement**: CI should check the merged manifest and dependency tree for SDKs that send data.
- **A2. No WebView loading developer-controlled content** that sends data. Open any links, such as the privacy policy, in the external browser.
- **A3. No "share logs / send feedback" feature that uploads automatically.** Bug reports are sent by testers themselves by email or form, outside the app.
Cloud/account-specific operational detail omitted; perform independent authorized preflight.
- **A5. Exports** are saved on the device (gallery or MediaStore). Any sharing goes through the Android share sheet, chosen by the user, which isn't collection by the app.
- **A6.** Re-check every release. Google requires the form to stay accurate and to be updated when practices change (answer/10787469).

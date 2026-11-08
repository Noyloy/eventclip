# Historical source README (not current build instructions)

Legacy diagnostic fixture packs are excluded from this snapshot. Follow the root public README for signing and current build limitations; do not execute device/cloud commands without separate approval.

# EventClip spike (local-only)

Private Android-only Expo prototype. **No git repo. No EAS/cloud builds. No personal media.**

## Layout
- `app/` — Expo SDK 58 app + local Media3 module `modules/eventclip-render`
- `harness/` — Node beat detector + compose-spec tests (host measurements)
- `fixtures/` — synthetic DRM-free media (`SYNTHETIC_FIXTURE`)
- `.tools/` — Temurin JDK 17 + Android SDK (isolated)
- `reports/` — measured outputs and logs

## Host tests (available now)
```bash
source <PROJECT_ROOT>/env.sh
cd <PROJECT_ROOT>/harness && npm run test:beats && npm run test:compose-spec
```

## Android debug APK (local)
```bash
source <PROJECT_ROOT>/env.sh
cd <PROJECT_ROOT>/app
npx expo prebuild --platform android
cd android && ./gradlew assembleDebug
```

## Device (later — wireless adb)
Parent coordinates pairing. Then:
```bash
adb connect <phone>
adb push <PROJECT_ROOT>/fixtures /data/local/tmp/eventclip-fixtures
adb install -r app/android/app/build/outputs/apk/debug/app-debug.apk
```

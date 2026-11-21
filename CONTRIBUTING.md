# Contributing

Thanks for helping with AA Action Buttons.

## Host vs container

- **Host:** Android Studio (optional), USB `adb`, pairing.
- **Devcontainer:** Gradle + SDK builds only — no Android Studio. The image matches the host CPU. On arm64, build-tools natives come from pinned AOSP builds and the Desktop Head Unit is omitted (Google ships that binary for amd64). Docker Desktop on Mac does not pass USB to the container; use wireless debugging or install APKs with host `adb`.

## Build

Installable build (recommended; host allowlist enforced):

```shell
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Local debug (any media-browser caller allowed — DHU / tooling only):

```shell
./gradlew assembleDebug
```

Wireless reconnect from the container: pair once with `adb pair` ([docs](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi)), then put the phone's Wireless debugging **IP:port** in gitignored `.adb-devices` (see `.adb-devices.example`) and run `./gradlew adbConnect`.

```shell
./gradlew adbInstall
adb logcat -s AaActionButtons
```

`adbInstall` installs the release APK on every connected device and reconnects `.adb-devices` first when that file exists. `scripts/adb-install.sh [apk]` installs an existing file without rebuilding.

## Scope

- The **default** product is the **media-browser** Android Auto path (Unknown sources).
- Do **not** treat Play-installer spoofing / KingInstaller as a supported install method in docs or CI.
- Car App Library / templated experiments are out of default scope unless a change explicitly targets that path.
- Prefer small, focused diffs. Do not commit keystores (`*.jks` / `*.keystore`), secrets, or local SDK paths. The local signing key is `signing/debug.keystore`, created on first build and left untracked so container rebuilds keep a stable signature.

## Pull requests

- Describe what you changed and how you tested (phone + Android Auto, and/or DHU).
- Do not claim car-screen success from a desktop compile alone.
- CI uploads a **release** APK artifact (non-debuggable), not the open debug APK.

## Reporting Android Auto quirks

Include:

- Phone model and Android version
- Android Auto app version
- Whether Unknown sources is enabled
- In-app debug header lines for `customActionLimit` / client package (open the app from AA once, then Refresh in the phone UI)

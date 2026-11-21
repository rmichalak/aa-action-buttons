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
- Prefer small, focused diffs. Do not commit keystores (`*.jks` / `*.keystore`), `signing/keystore.properties`, secrets, or local SDK paths. Debug builds use gitignored `signing/debug.keystore` (password `android`, alias `androiddebugkey`), created on the first build. `assembleRelease` requires gitignored `signing/release.keystore` and `signing/keystore.properties`, and fails without them. Public GitHub Releases require that same release key (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) and stop if the secrets are missing. Forks use `assembleDebug`.

## Pull requests

- Describe what you changed and how you tested (phone + Android Auto, and/or DHU).
- Do not claim car-screen success from a desktop or CI build alone.
- `version.properties` is the version source of truth (`versionCode` and `versionName`).
- Checks (`.github/workflows/checks.yml`) runs on pull requests and pushes to `main`: unit tests, lint, and `assembleDebug`. The APK is not uploaded. Do not publish the debug APK as the default artifact.
- The public download is the GitHub Release asset `aa-action-buttons-X.Y.Z.apk` from the manual Release workflow (`.github/workflows/release.yml`, `workflow_dispatch` on `main`). Inputs are `version_name` (`x.y.z`) and `version_code`. A new version must raise `versionCode` and change `versionName`; the workflow then writes `version.properties`, runs tests, lint, and `assembleRelease`, commits `Release x.y.z` to `main`, and tags `vX.Y.Z`. Passing the version already in `version.properties` tags that commit and publishes it without another commit.

## Reporting Android Auto quirks

Include:

- Phone model and Android version
- Android Auto app version
- Whether Unknown sources is enabled
- In-app debug header lines for `customActionLimit` / client package (open the app from AA once, then Refresh in the phone UI)

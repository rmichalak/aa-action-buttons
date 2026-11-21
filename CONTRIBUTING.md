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
./gradlew adbInstallDebug
adb logcat -s AaActionButtons
```

`adbInstall` installs the release APK on every connected device and reconnects `.adb-devices` first when that file exists. `adbInstallDebug` installs the debug APK the same way. `scripts/adb-install.sh [apk]` installs an existing file without rebuilding.

## Scope

- The **default** product is the **media-browser** Android Auto path (Unknown sources).
- Do **not** treat Play-installer spoofing / KingInstaller as a supported install method in docs or CI.
- Car App Library / templated experiments are out of default scope unless a change explicitly targets that path.
- Prefer small, focused diffs. Do not commit keystores (`*.jks` / `*.keystore`), `signing/keystore.properties`, secrets, or local SDK paths. Debug builds use gitignored `signing/debug.keystore` (password `android`, alias `androiddebugkey`), created on the first build. `assembleRelease` requires gitignored `signing/release.keystore` and `signing/keystore.properties`, and fails without them. Public GitHub Releases require that same release key (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) and stop if the secrets are missing. Forks use `assembleDebug`.

## Pull requests

- Describe what you changed and how you tested (phone + Android Auto, and/or DHU).
- Do not claim car-screen success from a desktop or CI build alone.
- `version.properties` is the next release (`versionCode` and `versionName`). The Release workflow ships that version, then raises the patch and `versionCode` by 1. Change a minor or major version by hand, and raise `versionCode` in that same commit.
- Checks (`.github/workflows/checks.yml`) runs on pull requests and pushes to `main`: unit tests, lint, and `assembleDebug`. The APK is not uploaded. Do not publish the debug APK as the default artifact.
- The public download is the GitHub Release asset `aa-action-buttons-X.Y.Z.apk` from the manual Release workflow (`.github/workflows/release.yml`, `workflow_dispatch` on `main`, no inputs). It tags the version already on `main` and publishes it. It refuses when `versionCode` is not higher than the last released tag. After publish, it pushes the patch bump to `main`.
- That push needs the `RELEASE_TOKEN` Actions secret: a fine-grained personal access token for this repository only, with Contents set to Read and write. The token owner must be able to bypass the `main` ruleset (repository admin). A personal account cannot add GitHub Actions itself to the bypass list, so the default `GITHUB_TOKEN` cannot push the version bump. Create the token at https://github.com/settings/personal-access-tokens/new and store it with `gh secret set RELEASE_TOKEN`. Do not commit the token.

## Reporting Android Auto quirks

Include:

- Phone model and Android version
- Android Auto app version
- Whether Unknown sources is enabled
- In-app debug header lines for `customActionLimit` / client package (open the app from AA once, then Refresh in the phone UI)

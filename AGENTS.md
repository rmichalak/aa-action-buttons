# AGENTS.md

Orientation for coding agents and maintainers working in this repository.

## Product posture

- **AA Action Buttons** — sideloaded Android Auto **media** app that shows up to 8 buttons and runs Tasker / HTTP / intent actions on the phone.
- **Not** a Play-approved IoT/template Auto app; **not** affiliated with Google, Android Auto, or Tasker.
- User-facing story: [README.md](README.md). Privacy: [PRIVACY.md](PRIVACY.md). License: MIT ([LICENSE](LICENSE)).

## Identity

| Item | Value |
| --- | --- |
| Gradle root | `aa-action-buttons` ([settings.gradle.kts](settings.gradle.kts)) |
| applicationId / namespace | `dev.robertmichalak.aaactionbuttons` |
| Launcher label | AA Action Buttons |
| Logcat tag | `AaActionButtons` |

Changing `applicationId` requires uninstalling the old package; SharedPreferences do not migrate.

## Layout

```text
app/src/main/kotlin/dev/robertmichalak/aaactionbuttons/
  ButtonsService.kt      MediaBrowserServiceCompat — AA browse + play/custom-action entry
  ActionRunner.kt        Runs Tasker / HTTP / intent for a ButtonConfig
  TaskerInvoker.kt       Tasker ACTION_TASK broadcast (TaskerIntent shape: id: URI, version 1.1)
  TaskerTasks.kt         Query named tasks for import UI
  ButtonStore.kt         SharedPreferences persistence + change broadcast
  ButtonCodec.kt         button JSON codec
  CallerAllowlist.kt     media-browser caller allowlist
  ButtonCatalog.kt       Defaults when no saved list (currently empty)
  ConfigActivity.kt      Phone CRUD / Tasker import / debug log
  AaDiagnostics.kt       Persists last onGetRoot host hints
  DebugLog.kt            On-device ring buffer
  model/Action.kt        Tasker | Http | IntentAction
  model/ButtonConfig.kt
app/src/main/res/xml/automotive_app_desc.xml   uses name="media"
version.properties       versionCode + versionName (source of truth)
.devcontainer/           JDK 21 + Android SDK image for the host CPU; GitHub CLI via the github-cli feature. arm64 swaps in pinned AOSP build-tools (Commit451, 36.0.0) and Ubuntu adb, and skips DHU.
```

## Build / run

```shell
./gradlew assembleRelease
# Installable: app/build/outputs/apk/release/app-release.apk (non-debuggable, host allowlist)
# Release key: gitignored signing/release.keystore via signing/keystore.properties.
# assembleRelease fails when that key is missing. Forks use assembleDebug.
# Public releases use that same key (Actions: ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD).
./gradlew assembleDebug
# Local/DHU only: any media-browser caller allowed. Signed with signing/debug.keystore.
```

**Host vs container:** Android Studio / USB `adb` on the host. Devcontainer = Gradle + SDK only (no Studio). Docker Desktop on Mac cannot USB-passthrough the phone — wireless `adb` or host-side install.

Wireless: pair once with `adb pair` ([docs](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi)). Reconnect with `./gradlew adbConnect` (gitignored `.adb-devices`; example `.adb-devices.example`). Devcontainer runs `scripts/adb-connect.sh --if-configured` on start. `./gradlew adbInstall` builds the release APK, reconnects `.adb-devices` when that file exists, and installs on every device in state `device`. `./gradlew adbInstallDebug` does the same with the debug APK.

`version.properties` is the next release (`versionCode` and `versionName`). Checks (`.github/workflows/checks.yml`) runs on pull requests and pushes to main: unit tests, lint, and `assembleDebug`. The APK is not uploaded. Release (`.github/workflows/release.yml`) is manual `workflow_dispatch` from main and takes no inputs. It ships the version already in `version.properties`, tags `vX.Y.Z`, and publishes GitHub Release asset `aa-action-buttons-X.Y.Z.apk` (the public download). It then raises the patch version and `versionCode` by 1 and pushes that to `main`. It refuses when `versionCode` is not higher than the last released tag. A minor or major change is a normal commit that also raises `versionCode`. The push authenticates with the `RELEASE_TOKEN` secret (fine-grained PAT, Contents read and write on this repo). A personal account cannot exempt GitHub Actions from a ruleset. These two workflows replaced a single `android.yml`. Release builds keep the host allowlist (`BuildConfig.DEBUG == false`). Do not publish the debug APK as the default artifact. A desktop or CI build does not prove Android Auto car-screen behavior.

## Android Auto constraints (do not “fix” incorrectly)

- **Custom browse actions** are host-advertised via `BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT`. Projected AA (`gearhead`) often reports `0` → app falls back to **playable** items.
- Idle session uses **empty metadata + `STATE_NONE`** so open prefers the browse list. After a playable tap, brief **Loading / Failed to load** then return to list is expected.
- Do **not** document or automate Play-installer spoofing as a supported path.
- Car App Library / templated UI needs a trusted install; out of default scope.
- Never claim car success from compile-only verification.
- DHU is installed only in the amd64 image (Google publishes that binary for linux/amd64). It needs a display; `desktop-head-unit --usb` needs USB passthrough (not available in Mac Docker Desktop).

## Conventions

- Small diffs; match existing Kotlin style; no new deps without need.
- No secrets, keystores, or machine-local paths in git.
- Prefer updating README/AGENTS when behavior or identity changes.

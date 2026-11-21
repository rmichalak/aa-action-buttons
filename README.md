# AA Action Buttons

Open-source Android app that shows a short list of action buttons on **Android Auto** and runs each action on the phone: a **Tasker** task, an **HTTP** request, or a generic **intent**.

Package: `dev.robertmichalak.aaactionbuttons`  
Not affiliated with Google, Android Auto, or Tasker.

## What this is

A **personal / power-user** tool. It appears in Android Auto as a **media** app (Unknown sources) because that is the sideload path that works without Play approval. Tapping a button uses the media “play” path, so you may briefly see the player UI (Loading / Failed to load) before returning to the list — that is expected host behavior, not a broken action.

It is **not**:

- A Google Play–approved Android Auto IoT/template app
- An official Tasker add-on
- A full smart-home client (use Home Assistant / openHAB Auto apps for that)

Car App Library / templated UI experiments need a Play-trusted install and are out of scope for the default media build.

## Requirements

- Android phone (min SDK 28) with Android Auto
- Android Auto **developer mode** → **Unknown sources** (for this sideloaded media app)
- Optional: [Tasker](https://tasker.joaoapps.com/) with **Allow External Access** enabled

## Host vs container

| Where | What it’s for |
| --- | --- |
| **Host** (macOS / Linux / Windows) | Android Studio (optional), USB `adb`, pairing phones over USB |
| **Devcontainer** (this repo’s Docker image) | JDK 21, Android SDK, `./gradlew` builds — **no Android Studio** |

Docker Desktop on a Mac **does not** pass phone USB into the container, and you generally **cannot** turn that on. Prefer:

- **Wireless debugging** so `adb` inside the container can reach the phone, or
- Build in the container, copy the APK out, and install with **host** `adb` over USB.

## Build

```shell
./gradlew assembleRelease
```

Installable APK (non-debuggable, debug-signed, host allowlist enforced):  
`app/build/outputs/apk/release/app-release.apk`

Debug and release APKs are signed with `signing/debug.keystore` (gitignored, debug password `android`). The first build creates it in the project directory. That directory is mounted from the host, so rebuilding the devcontainer keeps the same signature and a later `adb install -r` can update the app. If the file is missing, the next build makes a new key and Android rejects the update until you uninstall. A CI checkout creates its own keystore, so a GitHub artifact does not match this local key.

For local DHU / Media Controller experiments that need any media-browser client:

```shell
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk — allows any media-browser caller
```

```shell
adb uninstall dev.robertmichalak.carbuttons   # only if that older package is still installed
./gradlew adbInstall
```

`adbInstall` builds the release APK, reconnects the endpoints in `.adb-devices` when that file exists, and runs `adb install -r` on every device in state `device`. With no `.adb-devices` file it still installs over USB. It does not replace `dev.robertmichalak.carbuttons`; that id is a different app. Pass another apk to the script directly, for example `scripts/adb-install.sh app/build/outputs/apk/debug/app-debug.apk`.

### Wireless adb from the container

Pair once using Google's [Connect to a device over Wi-Fi](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi) steps. On the phone: **Wireless debugging → Pair device with pairing code**, then `adb pair <ip>:<pairing-port>`. Save the **IP address & port** from the main Wireless debugging screen (a different port) and reconnect with it:

```shell
cp .adb-devices.example .adb-devices
# set host:port in .adb-devices, then:
./gradlew adbConnect
```

`.adb-devices` is gitignored. The devcontainer runs the same connect when it starts (and continues if the phone is offline). The connect port changes when wireless debugging is toggled or the phone reboots; update the file when `adb devices` is empty. Rebuilding the container creates a new adb key, so pair once more after a rebuild.

CI on `main` uploads the **release** APK artifact (`app-release`), not the open debug build.

## Android Auto developer mode

1. Open Android Auto settings on the phone (Android 10+: Settings → Apps → Android Auto → Additional settings in the app).
2. Scroll to **About** / version and tap the version area about **10 times**. Confirm **Allow development settings?**
3. Overflow menu → **Developer settings** → enable **Unknown sources**.
4. Restart Android Auto if the app does not appear.

## Phone setup

1. Open **AA Action Buttons** and add up to 8 buttons (or import named Tasker tasks).
2. Grant Tasker run permission when prompted (or `adb shell pm grant dev.robertmichalak.aaactionbuttons net.dinglisch.android.tasker.PERMISSION_RUN_TASKS`).
3. Enable **Unknown sources** (above).
4. On the car / DHU, open the app under **media** apps and tap a button.

Debug: in-app log, or `adb logcat -s AaActionButtons`.

## Tasker

1. Tasker → Prefs → Misc → **Allow External Access**.
2. Button task names must match Tasker task names exactly.
3. Confirm package: `adb shell pm list packages | grep -i tasker` (usually `net.dinglisch.android.taskerm`).

Cold-start check:

```shell
adb shell am force-stop net.dinglisch.android.taskerm
```

Then trigger the task from Android Auto.

## Desktop Head Unit (DHU)

See [Test using the Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).

This image installs the Linux DHU at `/opt/android-sdk/extras/google/auto/desktop-head-unit`. The DHU needs a **display** (X11/Wayland in the environment).

1. On the phone, in Android Auto developer settings, **Start head unit server**.
2. Forward the port (wireless or host USB `adb`):

```shell
adb forward tcp:5277 tcp:5277
/opt/android-sdk/extras/google/auto/desktop-head-unit
```

Accessory mode (`desktop-head-unit --usb`) needs USB device passthrough, which Docker Desktop on a Mac usually cannot provide — use a host-side DHU or wireless head-unit server instead.

## Known limitations

| Topic | Reality |
| --- | --- |
| Custom browse actions | Host (`gearhead`) reports unsupported → playable media fallback |
| After tap | Brief Loading / Failed to load, then back to list (idle session has no fake track) |
| Templated / IoT Auto UI | Needs Play-trusted install; not the default build |
| Button cap | 8 by design |
| Debug APK | Any media-browser app can connect — use release APK for daily install |

## Docs

- [CONTRIBUTING.md](CONTRIBUTING.md) — build, PRs, bug reports
- [AGENTS.md](AGENTS.md) — project map for maintainers and coding agents
- [PRIVACY.md](PRIVACY.md) — what is stored and what may leave the device

## License

[MIT](LICENSE) — Copyright (c) 2026 Robert Michalak.

## Disclaimer

Use at your own risk while driving only for actions that are safe and legal. This project is unofficial and may break when Android Auto changes host behavior.

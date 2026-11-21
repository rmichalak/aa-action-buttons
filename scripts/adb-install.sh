#!/usr/bin/env bash
# Install an APK on every adb device in the "device" state.
# When .adb-devices exists, reconnect those wireless endpoints first.
# Pairing stays manual (`adb pair`).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
default_apk="$root/app/build/outputs/apk/release/app-release.apk"

usage() {
  echo "usage: scripts/adb-install.sh [apk]" >&2
  echo "Default apk: app/build/outputs/apk/release/app-release.apk" >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ $# -gt 1 ]]; then
  usage
  exit 2
fi

apk="${1:-$default_apk}"
if [[ "$apk" != /* ]]; then
  apk="$root/$apk"
fi

if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  echo "Build it with ./gradlew assembleRelease, or pass an apk path." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH." >&2
  exit 1
fi

if ! adb start-server >/dev/null; then
  echo "adb start-server failed." >&2
  exit 1
fi

# Missing .adb-devices is fine (USB). A stale wireless port must not hide a USB device.
bash "$root/scripts/adb-connect.sh" --if-configured

serials=()
while IFS= read -r serial; do
  [[ -z "$serial" ]] && continue
  serials+=("$serial")
done < <(adb devices | awk -F '\t' '$2 == "device" { print $1 }')

if [[ ${#serials[@]} -eq 0 ]]; then
  echo "No adb devices in state 'device'." >&2
  echo "Connect USB on the host, or set the wireless debugging IP:port in .adb-devices." >&2
  adb devices >&2 || true
  exit 1
fi

failed=0
for serial in "${serials[@]}"; do
  echo "adb -s $serial install -r $apk"
  if ! adb -s "$serial" install -r "$apk"; then
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

#!/usr/bin/env bash
# Connect adb to wireless-debugging endpoints listed in .adb-devices.
# Pairing stays manual (`adb pair`); this only reconnects.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
file="$root/.adb-devices"
best_effort=0

if [[ "${1:-}" == "--if-configured" ]]; then
  best_effort=1
elif [[ -n "${1:-}" ]]; then
  echo "usage: scripts/adb-connect.sh [--if-configured]" >&2
  exit 2
fi

finish() {
  local code="$1"
  if [[ "$best_effort" -eq 1 ]]; then
    exit 0
  fi
  exit "$code"
}

if [[ ! -f "$file" ]]; then
  if [[ "$best_effort" -eq 1 ]]; then
    exit 0
  fi
  echo "No .adb-devices file. Copy .adb-devices.example and set the phone's wireless debugging IP:port." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH." >&2
  finish 1
fi

trim() {
  local s="$1"
  s="${s#"${s%%[![:space:]]*}"}"
  s="${s%"${s##*[![:space:]]}"}"
  printf '%s' "$s"
}

targets=()
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%%#*}"
  line="${line%$'\r'}"
  line="$(trim "$line")"
  [[ -z "$line" ]] && continue
  if [[ ! "$line" =~ ^[A-Za-z0-9._%-]+:[0-9]+$ ]]; then
    echo "Invalid .adb-devices entry: $line" >&2
    echo "Expected host:port, for example 192.168.0.2:34567" >&2
    finish 1
  fi
  port="${line##*:}"
  port_num=$((10#$port))
  if (( port_num < 1 || port_num > 65535 )); then
    echo "Invalid port in .adb-devices entry: $line" >&2
    finish 1
  fi
  targets+=("$line")
done < "$file"

if [[ ${#targets[@]} -eq 0 ]]; then
  if [[ "$best_effort" -eq 1 ]]; then
    exit 0
  fi
  echo ".adb-devices has no host:port entries." >&2
  exit 1
fi

if ! adb start-server >/dev/null; then
  echo "adb start-server failed." >&2
  finish 1
fi

failed=0
for target in "${targets[@]}"; do
  echo "adb connect $target"
  if output="$(timeout 10 adb connect "$target" 2>&1)"; then
    printf '%s\n' "$output"
    if ! grep -qE '(^|[^[:alnum:]_])(already )?connected to ' <<<"$output"; then
      failed=1
    fi
  else
    printf '%s\n' "$output" >&2
    failed=1
  fi
done

echo
adb devices

if adb devices | grep -q $'\tunauthorized'; then
  echo "This container's adb key is not trusted. Pair again: adb pair <ip>:<pairing-port>." >&2
  echo "Steps: https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi" >&2
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  if [[ "$best_effort" -eq 1 ]]; then
    echo "adb connect did not attach every device. Check Wireless debugging and .adb-devices." >&2
    exit 0
  fi
  exit 1
fi

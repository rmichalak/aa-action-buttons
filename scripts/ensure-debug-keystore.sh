#!/usr/bin/env bash
# Create signing/debug.keystore when it is missing. The file is gitignored and
# lives in the workspace mount, so a devcontainer rebuild keeps the same key.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
keystore="$root/signing/debug.keystore"

if [[ -f "$keystore" ]]; then
  exit 0
fi

mkdir -p "$root/signing"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/keytool" ]]; then
  keytool="${JAVA_HOME}/bin/keytool"
else
  keytool="$(command -v keytool)"
fi

"$keytool" -genkeypair \
  -keystore "$keystore" \
  -storetype PKCS12 \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

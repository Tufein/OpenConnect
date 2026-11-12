#!/usr/bin/env bash
# Check that an APK is signed with the key in a keystore (and by nobody else).
# Usage: verify-apk-signer.sh <apk> <keystore> <alias> <password> [apksigner]
set -euo pipefail
apk="${1:?apk}"; keystore="${2:?keystore}"; alias_name="${3:?alias}"; password="${4:?password}"
apksigner="${5:-}"
if [ -z "$apksigner" ]; then
  tools="${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools"
  apksigner="${tools}/$(ls "$tools" | sort -V | tail -n 1)/apksigner"
fi

want="$(keytool -list -v -keystore "$keystore" -storepass "$password" -alias "$alias_name" 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1 | tr -d ':' | tr 'A-F' 'a-f')"
signers="$("$apksigner" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: *//p')"
count="$(printf '%s\n' "$signers" | grep -c . || true)"

[ -n "$want" ] || { echo "Could not read the key fingerprint from the keystore."; exit 1; }
[ "$count" -eq 1 ] || { echo "Expected exactly one signer, found ${count}."; exit 1; }
[ "$signers" = "$want" ] || { echo "The APK is signed by ${signers}, not by the release key ${want}."; exit 1; }
"$apksigner" verify "$apk" >/dev/null
echo "OK: the APK is signed with the release key (SHA-256 ${want})."

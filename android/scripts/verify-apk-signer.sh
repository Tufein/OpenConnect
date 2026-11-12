#!/usr/bin/env bash
# Check that an APK is signed with the key in a keystore (and by nobody else).
# Usage: verify-apk-signer.sh <apk> <keystore> <alias> <password> [apksigner]
set -euo pipefail
apk="${1:?apk}"; keystore="${2:?keystore}"; alias_name="${3:?alias}"; password="${4:?password}"
apksigner="${5:-}"
if [ -z "$apksigner" ]; then
  tools="${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools"
  # Prefer the build-tools this was developed against; fall back to the newest installed.
  preferred="${APKSIGNER_BUILD_TOOLS:-35.0.0}"
  if [ -x "${tools}/${preferred}/apksigner" ]; then
    apksigner="${tools}/${preferred}/apksigner"
  else
    apksigner="${tools}/$(ls "$tools" | sort -V | tail -n 1)/apksigner"
  fi
fi

want="$(keytool -list -v -keystore "$keystore" -storepass "$password" -alias "$alias_name" 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1 | tr -d ':' | tr 'A-F' 'a-f')"
# Capture the whole report first so it can be shown if it is not in the expected shape. Different
# apksigner versions word the signer line differently ("Signer #1 ..." or "Signer (minSdkVersion=...) ..."),
# so match on the digest part only.
report="$("$apksigner" verify --verbose --print-certs "$apk" 2>&1)" || { echo "apksigner could not verify the APK:"; echo "$report"; exit 1; }
signers="$(printf '%s\n' "$report" | sed -n 's/^Signer.*certificate SHA-256 digest: *//p' | tr 'A-F' 'a-f')"
count="$(printf '%s\n' "$signers" | grep -c . || true)"

[ -n "$want" ] || { echo "Could not read the key fingerprint from the keystore."; exit 1; }
[ "$count" -eq 1 ] || { echo "Expected exactly one signer, found ${count}. apksigner said:"; echo "$report"; exit 1; }
[ "$signers" = "$want" ] || { echo "The APK is signed by ${signers}, not by the release key ${want}."; exit 1; }
"$apksigner" verify "$apk" >/dev/null
echo "OK: the APK is signed with the release key (SHA-256 ${want})."

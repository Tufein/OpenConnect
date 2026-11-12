#!/usr/bin/env bash
# Provide the release signing key to a CI build, without it ever being in the repository.
#
# Usage (CI only): signing-key.sh <work-dir>
# Needs: GH_TOKEN with contents:write, GITHUB_REPOSITORY, GITHUB_ENV, keytool, openssl, the gh CLI.
#
# Where the key comes from, in order:
#   1. GitHub secrets OPENCONNECT_KEYSTORE_BASE64 + OPENCONNECT_KEYSTORE_PASSWORD (the better home;
#      pass them in as env vars). Alias is OPENCONNECT_KEY_ALIAS or "openconnect".
#   2. A private DRAFT release called "Signing key (private, never publish)" in this repository. Draft
#      releases and their assets are visible only to people with write access. The first release creates
#      the key there; every later release reuses it, so new APKs install over old ones.
#
# If neither exists but an APK was released before, this stops instead of making a new key: a new key
# would make every phone refuse the update. Set ALLOW_NEW_SIGNING_KEY=true to accept that on purpose.
set -euo pipefail

work="${1:?usage: signing-key.sh <work-dir>}"
mkdir -p "$work"
alias_name="${OPENCONNECT_KEY_ALIAS:-openconnect}"
store="$work/openconnect-release.p12"
draft_name="Signing key (private, never publish)"
asset_name="openconnect-release.p12"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

export_to_env() {
  local password="$1"
  echo "::add-mask::${password}"
  {
    echo "OPENCONNECT_KEYSTORE=${store}"
    echo "OPENCONNECT_KEYSTORE_PASSWORD=${password}"
    echo "OPENCONNECT_KEY_ALIAS=${alias_name}"
    echo "OPENCONNECT_REQUIRE_RELEASE_KEY=true"
  } >> "${GITHUB_ENV:?GITHUB_ENV is not set}"
}

# 1. GitHub secrets
if [ -n "${OPENCONNECT_KEYSTORE_BASE64:-}" ] && [ -n "${OPENCONNECT_KEYSTORE_PASSWORD:-}" ]; then
  echo "Using the signing key from GitHub secrets."
  printf '%s' "$OPENCONNECT_KEYSTORE_BASE64" | base64 -d > "$store"
  export_to_env "$OPENCONNECT_KEYSTORE_PASSWORD"
  exit 0
fi

# 2. Private draft release
draft_id="$(gh api "repos/${repo}/releases?per_page=100" --paginate \
  --jq ".[] | select(.draft == true and .name == \"${draft_name}\") | .id" | head -n 1)"

if [ -n "$draft_id" ]; then
  echo "Reusing the signing key stored in the private draft release ${draft_id}."
  asset_id="$(gh api "repos/${repo}/releases/${draft_id}" \
    --jq ".assets[] | select(.name == \"${asset_name}\") | .id" | head -n 1)"
  [ -n "$asset_id" ] || { echo "The draft release has no ${asset_name} asset."; exit 1; }
  gh api -H "Accept: application/octet-stream" "repos/${repo}/releases/assets/${asset_id}" > "$store"
  password="$(gh api "repos/${repo}/releases/${draft_id}" --jq .body | tr -d '\r' | sed -n 's/^password=//p' | head -n 1)"
  [ -n "$password" ] || { echo "The draft release body has no password= line."; exit 1; }
  export_to_env "$password"
  exit 0
fi

previous_apks="$(gh api "repos/${repo}/releases?per_page=100" --paginate \
  --jq '.[] | select(.draft == false) | .assets[].name | select(endswith(".apk"))' | wc -l)"
if [ "${previous_apks}" -gt 0 ] && [ "${ALLOW_NEW_SIGNING_KEY:-false}" != "true" ]; then
  echo "A signed APK was released before, but no signing key was found (secrets or draft release)."
  echo "A new key would stop existing installs from updating. Restore the key, or rerun with"
  echo "ALLOW_NEW_SIGNING_KEY=true to accept a new key on purpose."
  exit 1
fi

echo "First release: creating a new signing key."
password="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-40)"
[ "${#password}" -eq 40 ] || { echo "Could not generate a password."; exit 1; }
echo "::add-mask::${password}"
keytool -genkeypair -storetype PKCS12 -keystore "$store" -alias "$alias_name" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$password" -keypass "$password" \
  -dname "CN=OpenConnect release, O=OpenConnect" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
[ -s "$store" ] || { echo "keytool did not create the keystore."; exit 1; }

notes="$(printf 'Signing key for OpenConnect APKs. PRIVATE: only people with write access can see this draft.\nNever publish this release. Back up the file and the password, and keep them out of the repository.\n\nalias=%s\npassword=%s\n' "$alias_name" "$password")"
gh release create "signing-key" "$store" --repo "$repo" --draft \
  --title "$draft_name" --notes "$notes"
echo "The key is stored in a private draft release. Move it to GitHub secrets when convenient (docs/ANDROID.md)."
export_to_env "$password"

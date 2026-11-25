#!/usr/bin/env bash
# Verify that a store build contains neither the in-app updater's install permission nor its FileProvider.
#
# Usage:
#   verify-store-aab.sh <file.aab|file.apk> [path-to-aapt2]
#   AAPT2=/path/to/aapt2 verify-store-aab.sh <file>
#
# The argument is required: one Android App Bundle (.aab) or APK (.apk), normally
#   app/build/outputs/bundle/storeRelease/app-store-release.aab
#   app/build/outputs/apk/store/release/app-store-release.apk
# A second argument, or the AAPT2 environment variable, selects aapt2. Without either, the newest
# $ANDROID_HOME/build-tools/*/aapt2 is used when it is executable.
#
# The check is deliberately offline and self-contained: no Play/F-Droid service, no network, no bundletool.
# - APK: aapt2 prints the merged manifest/permissions; raw manifest bytes are checked as a fallback too.
# - AAB: aapt2 cannot read an AAB, so every */manifest/AndroidManifest.xml entry is extracted and its
#   protobuf string pool is searched for the exact permission and FileProvider symbols. The store manifest
#   is UTF-8; the NUL-stripped pass also covers a UTF-16 string pool if one appears.
#
# Exit codes: 0 = clean, 1 = forbidden component, bad input or tool failure.
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: verify-store-aab.sh <file.aab|file.apk> [path-to-aapt2]

  file          required: an Android App Bundle or APK built from the `store` flavor
  path-to-aapt2 optional; defaults to $AAPT2 or $ANDROID_HOME/build-tools/*/aapt2
EOF
}

die() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

file="${1:-}"
[ -n "$file" ] || { usage; die "the AAB/APK path is required"; }
[ -f "$file" ] || die "not a file: $file"

case "$file" in
  *.aab|*.apk) ;;
  *) die "expected a .aab or .apk file, got: $file" ;;
esac

aapt2="${2:-${AAPT2:-}}"
if [ -z "$aapt2" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -n "$sdk" ]; then
    for candidate in "$sdk"/build-tools/*/aapt2; do
      if [ -x "$candidate" ]; then aapt2="$candidate"; fi
    done
  fi
fi
if [ -n "$aapt2" ] && [ ! -x "$aapt2" ]; then
  die "aapt2 is not executable: $aapt2"
fi

work="$(mktemp -d "${TMPDIR:-/tmp}/verify-store.XXXXXX")"
trap 'rm -rf "$work"' EXIT

# The one place that defines what a store build must not contain. Keep these exact symbols: the GitHub
# flavor declares REQUEST_INSTALL_PACKAGES and androidx.core.content.FileProvider with the
# ${applicationId}.fileprovider authority.
forbidden='REQUEST_INSTALL_PACKAGES|androidx\.core\.content\.FileProvider|\.fileprovider'

# BusyBox's unzip has no -Z; `unzip -l` is portable enough for these paths (they contain no spaces).
zip_entries() {
  unzip -l "$1" | awk 'NR > 3 && $1 ~ /^[0-9]+$/ { print $4 }'
}

scan_manifest() {
  local manifest="$1" label="$2"
  if grep -a -E "$forbidden" "$manifest" >/dev/null 2>&1; then
    echo "Forbidden updater component in ${label}:" >&2
    grep -a -o -E "$forbidden" "$manifest" | sort -u >&2 || true
    return 1
  fi
  # AXML/protobuf strings can be UTF-16; strip NULs and try the same words again.
  if tr -d '\000' < "$manifest" | grep -a -E "$forbidden" >/dev/null 2>&1; then
    echo "Forbidden updater component in ${label} (UTF-16 string pool):" >&2
    tr -d '\000' < "$manifest" | grep -a -o -E "$forbidden" | sort -u >&2 || true
    return 1
  fi
  return 0
}

case "$file" in
  *.aab)
    entries="$(zip_entries "$file" | grep -E '(^|/)manifest/AndroidManifest\.xml$' || true)"
    [ -n "$entries" ] || die "no */manifest/AndroidManifest.xml found in the AAB: $file"

    i=0
    while IFS= read -r entry; do
      [ -n "$entry" ] || continue
      i=$((i + 1))
      manifest="$work/aab-manifest-${i}.bin"
      unzip -p "$file" "$entry" > "$manifest"
      scan_manifest "$manifest" "AAB entry ${entry}" || die "the AAB is not a store build"
    done <<EOF
$entries
EOF
    [ "$i" -gt 0 ] || die "no manifest entry could be read from the AAB"

    printf 'OK: %s is a store AAB: no REQUEST_INSTALL_PACKAGES and no FileProvider.\n' "$file"
    ;;

  *.apk)
    # Always check the raw binary manifest as well; this is what actually installs.
    zip_entries "$file" | grep -x 'AndroidManifest.xml' >/dev/null \
      || die "no AndroidManifest.xml at the APK root: $file"
    unzip -p "$file" AndroidManifest.xml > "$work/apk-manifest.bin"
    scan_manifest "$work/apk-manifest.bin" "APK manifest" || die "the APK is not a store build"

    if [ -n "$aapt2" ]; then
      if "$aapt2" dump permissions "$file" > "$work/aapt2-permissions.txt" 2>&1; then
        scan_manifest "$work/aapt2-permissions.txt" "aapt2 permissions" || die "the APK is not a store build"
      else
        echo "WARN: aapt2 could not dump permissions; the raw manifest check above still passed." >&2
      fi
      if "$aapt2" dump xmltree --file AndroidManifest.xml "$file" > "$work/aapt2-manifest.txt" 2>&1; then
        scan_manifest "$work/aapt2-manifest.txt" "aapt2 manifest" || die "the APK is not a store build"
      else
        echo "WARN: aapt2 could not dump the manifest; the raw manifest check above still passed." >&2
      fi
    else
      echo "note: aapt2 not found; checked the raw APK manifest only." >&2
    fi

    printf 'OK: %s is a store APK: no REQUEST_INSTALL_PACKAGES and no FileProvider.\n' "$file"
    ;;
esac

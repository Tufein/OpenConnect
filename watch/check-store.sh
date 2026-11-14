#!/usr/bin/env bash
# Checks what can be checked about the Connect IQ store packages without compiling them or logging in to Garmin:
# the generated manifest, properties, settings and strings, the version, the icon and the listing texts.
#
#   watch/check-store.sh            (exit 0 when everything is fine, 1 when something is not)
#
# With the Connect IQ SDK present (CIQ_SDK or ~/.Garmin/ConnectIQ/Sdks) and xmllint installed, the resource files are
# also validated against Garmin's own schema (resources.xsd). See docs/CONNECT_IQ_STORE.md.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
fail=0
ok() { echo "ok:   $*"; }
bad() { echo "FAIL: $*"; fail=1; }

version="$(sed -n 's/^version = "\(.*\)"$/\1/p' "$root/pyproject.toml" | head -n 1)"
[ -n "$version" ] && ok "project version $version" || bad "no version in pyproject.toml"
gradle_version="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$root/android/app/build.gradle.kts" | head -n 1)"
[ "$gradle_version" = "$version" ] && ok "the Android app has the same version" || bad "android versionName ($gradle_version) differs from $version"

sdk="${CIQ_SDK:-}"
if [ -z "$sdk" ]; then sdk="$(ls -d "$HOME"/.Garmin/ConnectIQ/Sdks/*/ 2>/dev/null | sort | tail -n 1 || true)"; sdk="${sdk%/}"; fi
xsd=""; [ -n "$sdk" ] && [ -f "$sdk/bin/resources.xsd" ] && xsd="$sdk/bin/resources.xsd"
have_xmllint=0; command -v xmllint >/dev/null 2>&1 && have_xmllint=1
[ "$have_xmllint" -eq 1 ] && [ -n "$xsd" ] && ok "Garmin's resources.xsd found, resources are validated against it" || echo "note: no SDK or no xmllint, so the resource files are not validated against Garmin's schema"

# The icon is a PNG (the store and monkeyc both want one).
sig="$(head -c 8 "$here/resources/drawables/launcher_icon.png" | od -An -tx1 | tr -d ' \n')"
[ "$sig" = "89504e470d0a1a0a" ] && ok "launcher icon is a PNG" || bad "resources/drawables/launcher_icon.png is not a PNG"

for kind in watchface widget; do
  rm -rf "$tmp/$kind"; mkdir -p "$tmp/$kind"
  cp -r "$here" "$tmp/$kind/watch"; cp "$root/pyproject.toml" "$tmp/$kind/"; rm -rf "$tmp/$kind/watch/gen" "$tmp/$kind/watch/out"
  if ! bash "$tmp/$kind/watch/build.sh" --package --type "$kind" --dry-run > "$tmp/$kind.log" 2>&1; then bad "$kind: build.sh --package --dry-run failed: $(tail -n 1 "$tmp/$kind.log")"; continue; fi
  gen="$tmp/$kind/watch/gen"
  grep -q "version=\"$version\"" "$gen/manifest.xml" && ok "$kind: manifest version $version" || bad "$kind: manifest version is not $version"
  products="$(grep -c '<iq:product id=' "$gen/manifest.xml")"
  [ "$products" -ge 1 ] && ok "$kind: $products products in the manifest" || bad "$kind: no products in the manifest"
  [ "$(grep -o '<iq:product id="[^"]*"' "$gen/manifest.xml" | sort | uniq -d | wc -l)" -eq 0 ] && ok "$kind: no product twice" || bad "$kind: a product is listed twice"
  grep -q 'type="'"$kind"'"' "$gen/manifest.xml" && ok "$kind: type is $kind" || bad "$kind: wrong type in the manifest"
  grep -q "__" "$gen/manifest.xml" && bad "$kind: a placeholder is left in the manifest" || ok "$kind: no placeholder left"
  for key in $(grep -o 'propertyKey="@Properties\.[A-Za-z0-9]*"' "$gen/resources/settings/settings.xml" | sed 's/.*Properties\.//; s/"//'); do
    grep -q "property id=\"$key\"" "$gen/resources/properties/properties.xml" || bad "$kind: setting $key has no property"
  done
  for id in $(grep -o '@Strings\.[A-Za-z0-9]*' "$gen/resources/settings/settings.xml" | sed 's/@Strings\.//' | sort -u); do
    for dir in resources resources-nld; do
      grep -q "string id=\"$id\"" "$tmp/$kind/watch/$dir/strings/settings_strings.xml" || bad "$kind: string $id missing in $dir"
    done
  done
  ok "$kind: every setting has a property and a string in English and Dutch"
  if [ "$have_xmllint" -eq 1 ]; then
    for f in manifest.xml resources/strings/strings.xml resources/properties/properties.xml resources/settings/settings.xml; do
      xmllint --noout "$gen/$f" 2>/dev/null && continue; bad "$kind: $f is not well-formed XML"
    done
    if [ -n "$xsd" ]; then
      for f in resources/strings/strings.xml resources/properties/properties.xml resources/settings/settings.xml; do
        xmllint --noout --schema "$xsd" "$gen/$f" >/dev/null 2>&1 && continue; bad "$kind: $f does not validate against resources.xsd"
      done
      for f in resources/strings/settings_strings.xml resources-nld/strings/settings_strings.xml resources/drawables/drawables.xml; do
        xmllint --noout --schema "$xsd" "$here/$f" >/dev/null 2>&1 && continue; bad "$f does not validate against resources.xsd"
      done
    fi
  fi
done

for f in docs/store/listing-en.md docs/store/listing-nl.md docs/store/SCREENSHOTS.md docs/CONNECT_IQ_STORE.md; do
  [ -s "$root/$f" ] && ok "$f exists" || bad "$f is missing"
done
id="$(sed -n 's/.*const val WATCHFACE_ID = "\(.*\)".*/\1/p' "$root/android/core/src/main/kotlin/nl/tufein/openconnect/core/watch/ConnectIqStore.kt")"
if [ -z "$id" ]; then echo "note: the app has no store link yet (ConnectIqStore.WATCHFACE_ID is empty): fill it in after the first upload"; else ok "the app links to the store listing $id"; fi

[ "$fail" -eq 0 ] && echo "All checks passed." || echo "Some checks failed."
exit "$fail"

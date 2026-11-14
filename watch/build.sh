#!/usr/bin/env bash
# Builds OpenConnect watch faces (.prg) for a Garmin watch with Garmin's Connect IQ tools.
#
#   watch/build.sh --device fenix847mm --design retro --color pink --fields steps,battery,heart,stress
#   watch/build.sh --device fenix847mm --design clean --all-colors      (one file per colour)
#   watch/build.sh --device fenix847mm --type widget --color mint       (widget with glance, instead of a face)
#   watch/build.sh --package --type watchface                           (one .iq with every product, for the Connect IQ store)
#
# Needs, once: the Connect IQ SDK and the watch's device files (Garmin's SDK Manager downloads both and asks for your
# Garmin login) and a developer key (--make-key creates one). See docs/WATCH_FACES.md.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
device="fenix847mm"; design="clean"; color="blue"; fields="battery,heart,stress,steps"
kind="watchface"; dry=0; make_key=0; all_colors=0; package=0;
products="fenix843mm,fenix847mm,fenix8pro47mm,fenix8solar47mm,fenix8solar51mm"
version="$(sed -n 's/^version = "\(.*\)"$/\1/p' "$(dirname "${BASH_SOURCE[0]}")/../pyproject.toml" 2>/dev/null | head -n 1 || true)"; version="${version:-0.0.0}"
 key="${CIQ_KEY:-$here/developer_key.der}"; sdk="${CIQ_SDK:-}"
palette="mint:5CF2C0 blue:7AA7FF amber:FFB000 pink:FF5FA2 green:9BBC0F"

die() { echo "build.sh: $*" >&2; exit 2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --device) device="${2:?}"; shift 2 ;;
    --design) design="${2:?}"; shift 2 ;;
    --color) color="${2:?}"; shift 2 ;;
    --fields) fields="${2:?}"; shift 2 ;;
    --key) key="${2:?}"; shift 2 ;;
    --sdk) sdk="${2:?}"; shift 2 ;;
    --type) kind="${2:?}"; shift 2 ;;
    --all-colors) all_colors=1; shift ;;
    --package) package=1; shift ;;
    --products) products="${2:?}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    --make-key) make_key=1; shift ;;
    -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option $1" ;;
  esac
done

colour_hex() {
  local c="$1" pair
  for pair in $palette; do
    if [ "${pair%%:*}" = "$c" ]; then echo "${pair##*:}"; return 0; fi
  done
  [[ "$c" =~ ^[0-9A-Fa-f]{6}$ ]] || die "color must be mint, blue, amber, pink, green or a 6 digit hex colour"
  echo "${c^^}"
}

case "$design" in clean|dashboard|retro) ;; *) die "design must be clean, dashboard or retro" ;; esac
case "$kind" in watchface|widget) ;; *) die "type must be watchface or widget" ;; esac
IFS=',' read -r -a list <<< "$fields"
[ "${#list[@]}" -eq 4 ] || die "fields needs four names, for example battery,heart,stress,steps"
for f in "${list[@]}"; do
  case "$f" in battery|heart|stress|steps|floors) ;; *) die "unknown field $f (battery, heart, stress, steps, floors)" ;; esac
done
[ "$(printf '%s\n' "${list[@]}" | sort -u | wc -l)" -eq 4 ] || die "fields must be four different names"
[[ "$device" =~ ^[A-Za-z0-9_]+$ ]] || die "device must be a Connect IQ device id such as fenix847mm"
[ "$package" -eq 0 ] || [ "$all_colors" -eq 0 ] || die "--package builds one file; use --color for its default colour"
IFS=',' read -r -a plist <<< "$products"
[ "${#plist[@]}" -ge 1 ] || die "products needs at least one device id"
for p in "${plist[@]}"; do [[ "$p" =~ ^[A-Za-z0-9_]+$ ]] || die "product $p is not a Connect IQ device id"; done
[ "$(printf '%s\n' "${plist[@]}" | sort -u | wc -l)" -eq "${#plist[@]}" ] || die "products must be different from each other"
if [ "$all_colors" -eq 0 ]; then hexes="$(colour_hex "$color")"; else
  hexes=""; for pair in $palette; do hexes="$hexes ${pair##*:}"; done
fi

gen="$here/gen"
# A widget ignores the design (it has one look); its file, app id and name say so.
if [ "$kind" = "widget" ]; then id_part="widget"; label="Widget"; entry="OpenConnectWidgetApp"
else id_part="$design"; label="${design^}"; entry="OpenConnectFaceApp"; fi
quoted="$(printf '"%s", ' "${list[@]}")"

# The settings Garmin's Connect IQ app shows for an app from the store, and the properties that hold their values.
# The defaults are the choices given to this build; the lists are the same palette and field names build.sh knows.
write_settings() {
  local hex="$1" pair name
  mkdir -p "$gen/resources/properties" "$gen/resources/settings"
  {
    echo "<properties>"
    [ "$kind" != "watchface" ] || echo "    <property id=\"Design\" type=\"string\">$design</property>"
    echo "    <property id=\"Colour\" type=\"number\">$((16#$hex))</property>"
    for i in 0 1 2 3; do echo "    <property id=\"Field$((i + 1))\" type=\"string\">${list[$i]}</property>"; done
    echo "</properties>"
  } > "$gen/resources/properties/properties.xml"
  {
    echo "<settings>"
    if [ "$kind" = "watchface" ]; then
      echo "    <setting propertyKey=\"@Properties.Design\" title=\"@Strings.DesignTitle\">"
      echo "        <settingConfig type=\"list\">"
      for name in clean dashboard retro; do echo "            <listEntry value=\"$name\">@Strings.Design${name^}</listEntry>"; done
      echo "        </settingConfig>"
      echo "    </setting>"
    fi
    echo "    <setting propertyKey=\"@Properties.Colour\" title=\"@Strings.ColourTitle\">"
    echo "        <settingConfig type=\"list\">"
    for pair in $palette; do
      name="${pair%%:*}"
      echo "            <listEntry value=\"$((16#${pair##*:}))\">@Strings.Colour${name^}</listEntry>"
    done
    echo "        </settingConfig>"
    echo "    </setting>"
    for i in 1 2 3 4; do
      echo "    <setting propertyKey=\"@Properties.Field$i\" title=\"@Strings.Field${i}Title\">"
      echo "        <settingConfig type=\"list\">"
      for name in battery heart stress steps floors; do echo "            <listEntry value=\"$name\">@Strings.Field${name^}</listEntry>"; done
      echo "        </settingConfig>"
      echo "    </setting>"
    done
    echo "</settings>"
  } > "$gen/resources/settings/settings.xml"
}

# Writes gen/ for one colour. A store package (--package) has one fixed app id per type, every product in its manifest
# and is configured from Garmin's Connect IQ app; a single build has one app id per design and colour, so several
# variants can sit on the watch side by side.
generate() {
  local hex="$1" app_id app_name prods="$device" block
  if [ "$package" -eq 1 ]; then
    # This id is what the Connect IQ store registers for the app on the first upload and checks on every later one:
    # it must never change.
    app_id="$(printf 'openconnect-store-%s' "$kind" | md5sum | cut -c1-32)"
    prods="$products"
    if [ "$kind" = "widget" ]; then app_name="OpenConnect Widget"; else app_name="OpenConnect"; fi
  else
    app_id="$(printf 'openconnect-%s-%s' "$id_part" "$hex" | md5sum | cut -c1-32)"
    app_name="OpenConnect ${label}"
  fi
  block=""
  for p in $(echo "$prods" | tr ',' ' '); do block="${block}            <iq:product id=\"$p\" />"$'\n'; done
  rm -rf "$gen"; mkdir -p "$gen/source" "$gen/resources/strings"
  cat > "$gen/source/Config.mc" <<CFG
(:glance)
module Config {
    const DESIGN = "$design";
    const COLOR = 0x$hex;
    const FIELDS = [${quoted%, }];
}
CFG
  cat > "$gen/resources/strings/strings.xml" <<STR
<strings>
    <string id="AppName">$app_name</string>
</strings>
STR
  write_settings "$hex"
  awk -v block="${block%$'\n'}" '/^__PRODUCTS__$/ { print block; next } { print }' "$here/manifest.xml" \
    | sed -e "s/__APP_ID__/$app_id/" -e "s/__ENTRY__/$entry/" -e "s/__TYPE__/$kind/" -e "s/__VERSION__/$version/" > "$gen/manifest.xml"
}

file_for() {
  if [ "$package" -eq 1 ]; then echo "openconnect-$kind-$version.iq"; else echo "openconnect-$id_part-$1-$device.prg"; fi
}

if [ "$dry" -eq 1 ]; then
  for hex in $hexes; do
    generate "$hex"
    echo "dry run: wrote $gen (device $device, design $design, color $hex, fields $fields)"
    echo "file: $(file_for "$hex")"
  done
  exit 0
fi

if [ "$make_key" -eq 1 ] && [ ! -f "$key" ]; then
  tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
  openssl genrsa -out "$tmp" 4096 2>/dev/null
  openssl pkcs8 -topk8 -inform PEM -outform DER -in "$tmp" -out "$key" -nocrypt
  echo "created developer key $key (keep it: later builds must use the same key)"
fi
[ -f "$key" ] || die "no developer key at $key; run again with --make-key or give --key"

if [ -z "$sdk" ]; then
  sdk="$(ls -d "$HOME"/.Garmin/ConnectIQ/Sdks/*/ 2>/dev/null | sort | tail -n 1 || true)"
  sdk="${sdk%/}"
fi
[ -n "$sdk" ] && [ -x "$sdk/bin/monkeyc" ] || die "Connect IQ SDK not found; set CIQ_SDK or pass --sdk (see docs/WATCH_FACES.md)"

mkdir -p "$here/out"
for hex in $hexes; do
  generate "$hex"
  if [ "$package" -eq 1 ]; then
    # -e packs every product of the manifest into one .iq; -r strips debug information, as the store wants.
    "$sdk/bin/monkeyc" -e -r -f "$here/monkey.jungle" -o "$here/out/$(file_for "$hex")" -y "$key" -w
    echo "packed $here/out/$(file_for "$hex") for the Connect IQ store (see docs/CONNECT_IQ_STORE.md)"
  else
    "$sdk/bin/monkeyc" -d "$device" -f "$here/monkey.jungle" -o "$here/out/$(file_for "$hex")" -y "$key" -w
    echo "built $here/out/$(file_for "$hex")"
  fi
done
[ "$package" -eq 1 ] || echo "Copy the file to the watch with the app (Settings, Watch face) or into GARMIN/Apps over USB."

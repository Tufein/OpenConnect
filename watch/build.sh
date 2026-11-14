#!/usr/bin/env bash
# Builds OpenConnect watch faces (.prg) for a Garmin watch with Garmin's Connect IQ tools.
#
#   watch/build.sh --device fenix847mm --design retro --color pink --fields steps,battery,heart,stress
#   watch/build.sh --device fenix847mm --design clean --all-colors      (one file per colour)
#
# Needs, once: the Connect IQ SDK and the watch's device files (Garmin's SDK Manager downloads both and asks for your
# Garmin login) and a developer key (--make-key creates one). See docs/WATCH_FACES.md.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
device="fenix847mm"; design="clean"; color="blue"; fields="battery,heart,stress,steps"
dry=0; make_key=0; all_colors=0; key="${CIQ_KEY:-$here/developer_key.der}"; sdk="${CIQ_SDK:-}"
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
    --all-colors) all_colors=1; shift ;;
    --dry-run) dry=1; shift ;;
    --make-key) make_key=1; shift ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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
IFS=',' read -r -a list <<< "$fields"
[ "${#list[@]}" -eq 4 ] || die "fields needs four names, for example battery,heart,stress,steps"
for f in "${list[@]}"; do
  case "$f" in battery|heart|stress|steps|floors) ;; *) die "unknown field $f (battery, heart, stress, steps, floors)" ;; esac
done
[ "$(printf '%s\n' "${list[@]}" | sort -u | wc -l)" -eq 4 ] || die "fields must be four different names"
[[ "$device" =~ ^[A-Za-z0-9_]+$ ]] || die "device must be a Connect IQ device id such as fenix847mm"
if [ "$all_colors" -eq 0 ]; then hexes="$(colour_hex "$color")"; else
  hexes=""; for pair in $palette; do hexes="$hexes ${pair##*:}"; done
fi

gen="$here/gen"
quoted="$(printf '"%s", ' "${list[@]}")"

# Writes gen/ for one colour; prints the file name the build will have.
generate() {
  local hex="$1"
  # One app id per design and colour, so several variants can sit on the watch side by side.
  local app_id
  app_id="$(printf 'openconnect-%s-%s' "$design" "$hex" | md5sum | cut -c1-32)"
  rm -rf "$gen"; mkdir -p "$gen/source" "$gen/resources/strings"
  cat > "$gen/source/Config.mc" <<CFG
module Config {
    const DESIGN = "$design";
    const COLOR = 0x$hex;
    const FIELDS = [${quoted%, }];
}
CFG
  cat > "$gen/resources/strings/strings.xml" <<STR
<strings>
    <string id="AppName">OpenConnect ${design^}</string>
</strings>
STR
  sed -e "s/__APP_ID__/$app_id/" -e "s/__PRODUCT__/$device/" "$here/manifest.xml" > "$gen/manifest.xml"
}

file_for() { echo "openconnect-$design-$1-$device.prg"; }

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
  "$sdk/bin/monkeyc" -d "$device" -f "$here/monkey.jungle" -o "$here/out/$(file_for "$hex")" -y "$key" -w
  echo "built $here/out/$(file_for "$hex")"
done
echo "Copy the file to the watch with the app (Settings, Watch face) or into GARMIN/Apps over USB."

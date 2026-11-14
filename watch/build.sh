#!/usr/bin/env bash
# Builds one OpenConnect watch face (.prg) for a Garmin watch with Garmin's Connect IQ tools.
#
#   watch/build.sh --device fenix847mm --design retro --color pink --fields steps,battery,heart,stress
#
# Needs, once: the Connect IQ SDK and the watch's device files (Garmin's SDK Manager downloads both and asks for your
# Garmin login) and a developer key (--make-key creates one). See docs/WATCH_FACES.md.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
device="fenix847mm"; design="clean"; color="blue"; fields="battery,heart,stress,steps"
dry=0; make_key=0; key="${CIQ_KEY:-$here/developer_key.der}"; sdk="${CIQ_SDK:-}"

die() { echo "build.sh: $*" >&2; exit 2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --device) device="${2:?}"; shift 2 ;;
    --design) design="${2:?}"; shift 2 ;;
    --color) color="${2:?}"; shift 2 ;;
    --fields) fields="${2:?}"; shift 2 ;;
    --key) key="${2:?}"; shift 2 ;;
    --sdk) sdk="${2:?}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    --make-key) make_key=1; shift ;;
    -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option $1" ;;
  esac
done

case "$design" in clean|dashboard|retro) ;; *) die "design must be clean, dashboard or retro" ;; esac
case "$color" in
  mint) hex=5CF2C0 ;; blue) hex=7AA7FF ;; amber) hex=FFB000 ;; pink) hex=FF5FA2 ;; green) hex=9BBC0F ;;
  *) [[ "$color" =~ ^[0-9A-Fa-f]{6}$ ]] || die "color must be mint, blue, amber, pink, green or a 6 digit hex colour"; hex="${color^^}" ;;
esac
IFS=',' read -r -a list <<< "$fields"
[ "${#list[@]}" -eq 4 ] || die "fields needs four names, for example battery,heart,stress,steps"
for f in "${list[@]}"; do
  case "$f" in battery|heart|stress|steps|floors) ;; *) die "unknown field $f (battery, heart, stress, steps, floors)" ;; esac
done
[ "$(printf '%s\n' "${list[@]}" | sort -u | wc -l)" -eq 4 ] || die "fields must be four different names"
[[ "$device" =~ ^[A-Za-z0-9_]+$ ]] || die "device must be a Connect IQ device id such as fenix847mm"

# One app id per design and colour, so several variants can sit on the watch side by side.
app_id="$(printf 'openconnect-%s-%s' "$design" "$hex" | md5sum | cut -c1-32)"
name="OpenConnect ${design^}"

gen="$here/gen"
rm -rf "$gen"; mkdir -p "$gen/source" "$gen/resources/strings"
quoted="$(printf '"%s", ' "${list[@]}")"
cat > "$gen/source/Config.mc" <<CFG
module Config {
    const DESIGN = "$design";
    const COLOR = 0x$hex;
    const FIELDS = [${quoted%, }];
}
CFG
cat > "$gen/resources/strings/strings.xml" <<STR
<strings>
    <string id="AppName">$name</string>
</strings>
STR
sed -e "s/__APP_ID__/$app_id/" -e "s/__PRODUCT__/$device/" "$here/manifest.xml" > "$gen/manifest.xml"

if [ "$dry" -eq 1 ]; then
  echo "dry run: wrote $gen (device $device, design $design, color $hex, fields $fields, app id $app_id)"
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
out="$here/out/openconnect-$design-$hex-$device.prg"
"$sdk/bin/monkeyc" -d "$device" -f "$here/monkey.jungle" -o "$out" -y "$key" -w
echo "built $out"
echo "Copy it to the watch with the app (Settings, Watch face) or into GARMIN/Apps over USB."

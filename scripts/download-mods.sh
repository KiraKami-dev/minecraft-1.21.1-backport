#!/bin/bash
# Download the NeoForge 1.21.1 mod set from Modrinth and verify every jar against mods/mods.lock.json.
# Usage: download-mods.sh <server|client> <dest dir>
#   server: mods with side server/both into <dest>; datapacks into <dest>/../datapacks-to-install
#   client: mods with side client/both into <dest>
# Files already present with the right sha512 are kept. Anything else in <dest> is left alone.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SIDE="${1:?usage: download-mods.sh <server|client> <dest dir>}"
DEST="${2:?usage: download-mods.sh <server|client> <dest dir>}"
case "$SIDE" in server|client) ;; *) echo "side must be server or client" >&2; exit 1;; esac
LOCK="${MODS_LOCK:-$ROOT/mods/mods.lock.json}"
mkdir -p "$DEST"; DEST="$(cd "$DEST" && pwd)"
if command -v sha512sum >/dev/null; then sum512() { sha512sum "$1" | cut -d' ' -f1; }; else sum512() { shasum -a 512 "$1" | cut -d' ' -f1; }; fi

# one line per file: sha512 <TAB> filename <TAB> url <TAB> type
python3 - "$LOCK" "$SIDE" <<'PY' > "$DEST/.lock-rows.tmp"
import json, sys
for m in json.load(open(sys.argv[1]))['mods']:
    if m['side'] in (sys.argv[2], 'both'):
        print('\t'.join([m['sha512'], m['filename'], m['url'] or '', m['type']]))
PY
ok=0; fail=0; total=0
while IFS=$'\t' read -r hash name url type; do
  total=$((total+1))
  target="$DEST/$name"
  if [ "$type" = datapack ]; then mkdir -p "$DEST/../datapacks-to-install"; target="$DEST/../datapacks-to-install/$name"; fi
  if [ -f "$target" ] && [ "$(sum512 "$target")" = "$hash" ]; then ok=$((ok+1)); continue; fi
  if [ -z "$url" ]; then echo "NO URL (not on Modrinth, place it by hand): $name" >&2; fail=$((fail+1)); continue; fi
  echo "downloading $name"
  if ! curl -sfL --retry 3 -o "$target.part" "$url" || [ "$(sum512 "$target.part")" != "$hash" ]; then
    rm -f "$target.part"; echo "FAILED or checksum mismatch: $name" >&2; fail=$((fail+1)); continue
  fi
  mv "$target.part" "$target"; ok=$((ok+1))
done < "$DEST/.lock-rows.tmp"
rm -f "$DEST/.lock-rows.tmp"
echo "$ok/$total files present and sha512-verified in $DEST ($fail problems)"
[ "$fail" -eq 0 ]

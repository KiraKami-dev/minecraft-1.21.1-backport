#!/bin/bash
# Backport a modded 1.21.11 Java world to 1.21.1 NeoForge. See README.md.
# Usage: convert.sh <backup .tar.gz | world dir> <out dir>   (env: CHUNKER_XMX, SCAN_WORKERS, SERVER_DIR, SMOKE_PORT)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IN="${1:?usage: convert.sh <backup .tar.gz | world dir> <out dir>}"
OUT="${2:?usage: convert.sh <backup .tar.gz | world dir> <out dir>}"
XMX="${CHUNKER_XMX:-8g}"
WORKERS="${SCAN_WORKERS:-6}"
SETTINGS="${BACKPORT_SETTINGS:-$ROOT/backport-settings.json}"
JAR="$ROOT/chunker/cli/build/libs/chunker-cli-1.21.0.jar"
mkdir -p "$OUT"; OUT="$(cd "$OUT" && pwd)"
[ -e "$OUT/world" ] && { echo "$OUT/world already exists, pick a fresh out dir" >&2; exit 1; }
t0=$(date +%s)
step() { echo; echo "== $(date +%H:%M:%S) $*"; }

step "1/7 source copy (read-only)"
SRC="$OUT/source"
if [ -d "$SRC" ]; then chmod -R u+w "$SRC"; rm -rf "$SRC"; fi
mkdir -p "$SRC"
if [ -d "$IN" ]; then cp -R "$IN"/. "$SRC"/; else tar -xzf "$IN" -C "$SRC"; fi
LEVEL="$(find "$SRC" -maxdepth 3 -name level.dat | head -1)"
[ -n "$LEVEL" ] || { echo "no level.dat found in the input" >&2; exit 1; }
WORLD="$(dirname "$LEVEL")"
chmod -R a-w "$SRC"
echo "world: $WORLD"

step "2/7 chunker jar"
if [ ! -f "$JAR" ] || [ -n "$(find "$ROOT/chunker/cli/src" -newer "$JAR" -type f | head -1)" ]; then
  (cd "$ROOT/chunker" && ./gradlew :cli:build -x test -q)
fi
ls -l "$JAR"

step "3/7 conversion (heap $XMX)"
mkdir -p "$OUT/logs"
java -Xmx"$XMX" -jar "$JAR" -i "$WORLD" -o "$OUT/world" -f JAVA_1_21_1 \
  --backport "$SETTINGS" --backportLog "$OUT/logs" > "$OUT/logs/chunker.log" 2>&1
tail -n 1 "$OUT/logs/chunker.log"

step "4/7 level.dat and datapacks"
python3 "$ROOT/scripts/fix_world.py" "$WORLD" "$OUT/world" "$OUT/logs/warnings.txt"

step "5/7 scans (exact block counts, source without pre-light chunks Chunker drops)"
SCAN_EXACT=1 SCAN_SKIP_PROTO=1 SCAN_NO_GRAVES=1 python3 "$ROOT/scripts/scan.py" "$WORLD" "$OUT/scan-source.json" "$WORKERS" > "$OUT/logs/scan-source.log" 2>&1
SCAN_EXACT=1 python3 "$ROOT/scripts/scan.py" "$OUT/world" "$OUT/scan-converted.json" "$WORKERS" > "$OUT/logs/scan-converted.log" 2>&1
# blockcheck compares the output block ids with the server mods (downloaded once into .cache/server-mods)
export BLOCKCHECK_MODS="${BLOCKCHECK_MODS:-$ROOT/.cache/server-mods}"
[ -d "$BLOCKCHECK_MODS" ] || "$ROOT/scripts/download-mods.sh" server "$BLOCKCHECK_MODS" > "$OUT/logs/download-mods.log" 2>&1 || true
python3 "$ROOT/scripts/blockcheck.py" "$OUT/world" > "$OUT/logs/blockcheck.log" 2>&1 || true

step "6/7 server smoke test (set SERVER_DIR=none to skip)"
# SERVER_DIR is a NeoForge 1.21.1 server directory (mods, config, libraries, run.sh, ...) used as a template: it is
# copied to <out>/server-test and never modified. Without one the smoke test is skipped (see docs/BACKPORT.md).
SERVER_DIR="${SERVER_DIR:-$ROOT/server-check}"
export SMOKE_PORT="${SMOKE_PORT:-25613}"
if [ "$SERVER_DIR" != none ] && [ -d "$SERVER_DIR/mods" ]; then
  "$ROOT/scripts/server_smoke.sh" "$SERVER_DIR" "$OUT/world" "$OUT" | tee "$OUT/logs/smoke-summary.txt"
else
  echo "skipped" > "$OUT/logs/smoke-summary.txt"
fi

step "7/7 summary"
{
  python3 "$ROOT/scripts/summary.py" "$OUT/scan-source.json" "$OUT/scan-converted.json" \
    "$OUT/logs/backport-items.log" "$ROOT/chunker/cli/src/main/java/com/hivemc/chunker/conversion/encoding/java/backport/BlockBackport.java"
  echo; echo "blockcheck:"; tail -n 3 "$OUT/logs/blockcheck.log"
  echo; echo "conversion report:"; sed -n '/# conversion report/,$p' "$OUT/logs/backport-items.log"
  echo; echo "warnings (data carried over but not converted):"; if [ -s "$OUT/logs/warnings.txt" ]; then cat "$OUT/logs/warnings.txt"; else echo "none"; fi
  echo; echo "server smoke test:"; [ -f "$OUT/logs/smoke-summary.txt" ] && cat "$OUT/logs/smoke-summary.txt"
  echo; echo "total time: $(( $(date +%s) - t0 ))s; converted world: $OUT/world"
} | tee "$OUT/summary.txt"

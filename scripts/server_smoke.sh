#!/bin/bash
# Smoke test: start a copy of the 1.21.1 server on the converted world, force-load areas around spawn and the players,
# save, stop, and list the data errors in the log. Usage: server_smoke.sh <server template dir> <world dir> <out dir>
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$1"; WORLD="$2"; OUT="$3"; PORT="${SMOKE_PORT:-25613}"
S="$OUT/server-test"; LOG="$OUT/logs/server-test.log"
JAVA="java"; [ -x /usr/libexec/java_home ] && JAVA="$(/usr/libexec/java_home -v 21 2>/dev/null)/bin/java"
rm -rf "$S"; mkdir -p "$S" "$OUT/logs"
for f in mods config libraries defaultconfigs run.sh user_jvm_args.txt eula.txt server.properties; do
  [ -e "$TEMPLATE/$f" ] && cp -R "$TEMPLATE/$f" "$S/"
done
sed -i.bak "s/^server-port=.*/server-port=$PORT/; s/^level-name=.*/level-name=world/" "$S/server.properties"; rm -f "$S/server.properties.bak"
cp -cR "$WORLD" "$S/world" 2>/dev/null || cp -R "$WORLD" "$S/world"
ARGS="$(ls "$S"/libraries/net/neoforged/neoforge/*/unix_args.txt | head -1)"
cd "$S"; mkfifo console
(tail -f console | "$JAVA" -Xms2G -Xmx${SMOKE_XMX:-5G} @"${ARGS#$S/}" nogui > "$LOG" 2>&1) &
RUNNER=$!
send() { echo "$1" > console; }
for i in $(seq 1 120); do grep -q 'Done (' "$LOG" 2>/dev/null && break; sleep 5; done
python3 "$ROOT/scripts/smoke_cmds.py" "$WORLD" > "$OUT/logs/smoke-cmds.txt"
while read -r c; do send "$c"; sleep 1; done < "$OUT/logs/smoke-cmds.txt"
N=$(wc -l < "$OUT/logs/smoke-cmds.txt")
for i in $(seq 1 360); do [ "$(grep -c 'to be force loaded' "$LOG")" -ge "$N" ] && break; sleep 5; done
sleep 60
send "save-all flush"; sleep 20; send stop
for i in $(seq 1 60); do grep -q 'Dispatching unloading event' "$LOG" && break; sleep 2; done; sleep 5
pkill -f "tail -f console"; pkill -f "$S/" 2>/dev/null; kill $RUNNER 2>/dev/null
python3 "$ROOT/scripts/log_errors.py" "$LOG"

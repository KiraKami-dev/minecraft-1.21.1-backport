#!/bin/sh
# Example only (macOS, TLauncher). Run scripts/download-mods.sh client <this dir>/mods first.
# Switch TLauncher's shared .minecraft to the NeoForge 1.21.1 backport mods. Undo with use-fabric.sh.
M="$HOME/Library/Application Support/minecraft"; B="$(cd "$(dirname "$0")" && pwd)"
[ -e "$M/mods-fabric-1.21.11" ] && { echo "Already on backport mods."; exit 0; }
mv "$M/mods" "$M/mods-fabric-1.21.11" && mv "$M/config" "$M/config-fabric-1.21.11" 2>/dev/null
mkdir -p "$M/config"; [ -d "$M/config-backport-1.21.1" ] && { rmdir "$M/config"; mv "$M/config-backport-1.21.1" "$M/config"; }
cp -R "$B/mods" "$M/mods" && echo "Backport mods active. In TLauncher pick neoforge-21.1.265."

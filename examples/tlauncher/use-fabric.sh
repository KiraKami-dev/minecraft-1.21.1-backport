#!/bin/sh
# Example only (macOS, TLauncher). Undo of use-backport.sh.
# Restore the Fabric 1.21.11 mods and config.
M="$HOME/Library/Application Support/minecraft"
[ -e "$M/mods-fabric-1.21.11" ] || { echo "Already on Fabric mods."; exit 0; }
rm -rf "$M/mods" && mv "$M/mods-fabric-1.21.11" "$M/mods"
mv "$M/config" "$M/config-backport-1.21.1" && [ -d "$M/config-fabric-1.21.11" ] && mv "$M/config-fabric-1.21.11" "$M/config"
echo "Fabric 1.21.11 mods restored."

# Client side: changes to the friends' installer

The friends install with the installer in [A1K2S3/minecraft](https://github.com/A1K2S3/minecraft) (`client/install.sh`,
`client/install.ps1`), driven by `mods.json` and `scripts/generate`. Today that is a Fabric 1.21.11 pack. After the
cutover everyone needs **NeoForge 21.1.265 for Minecraft 1.21.1** and the NeoForge mod list. This page lists what has
to change in that repo (this repo does not modify it) and how to do it by hand.

## What changes in A1K2S3/minecraft

| file | change |
|---|---|
| `mods.json` | `"minecraft": "1.21.1"`, `"loader": "neoforge"`, `"loader_version": "21.1.265"`. Replace the mod list with the NeoForge one from [`mods/mods.lock.json`](../mods/mods.lock.json) |
| `scripts/generate` | the Modrinth lookups are hard-wired to `mc_version="1.21.11"` and `loader="fabric"` (`resolve_latest`, and the two "no fabric/1.21.11 version found" errors); read both from `mods.json` instead |
| `client/install.sh`, `client/install.ps1` | install NeoForge instead of the Fabric loader profile (below) |
| `client/mods.generated.tsv`, `client/purge.generated.txt`, `server/mods.generated.env` | regenerated, never hand-edited |
| `server/docker-compose.yml`, `AGENTS.md`, `server/README.md` | server changes in [HOSTING.md](HOSTING.md); the docs still say Fabric 1.21.11 |

### mods.json from the lock file

Each lock entry maps one to one onto a `mods.json` entry:

| mods.lock.json | mods.json |
|---|---|
| `name` | `name` |
| `project` | `slug` |
| `side` | `side` (`server`, `client`, `both`) |
| `type` | `type` (`mod`, `datapack`) |
| `version_number` | `version` (the server pin) |
| `version_id` | `client_version` (the client pin; every friend gets the same jar) |
| (all tiers) | `client_tiers: null` |

The lock holds 57 `both`, 21 `client` and 18 `server` entries. `server` entries must stay out of the client (the purge
list below). Shader packs, resource packs and the tiers (`dalit`, `pandit`, `modi`) are not in the lock and carry over
from the current `mods.json` unchanged where they are loader independent (Complementary shaders, resource packs). The
lock's client set uses Iris 1.8.14 and Sodium 0.8.13 for 1.21.1; the old "Sodium 0.8.7 for Iris" pin and the tier config
that `install.sh` writes for Fabric-only mods (LambDynamicLights, Continuity, Sodium options) have to be re-checked
against the NeoForge builds. Continuity is not in the NeoForge set.

`scripts/generate` stores a SHA-256 per client file; Modrinth only gives SHA-512 and SHA-1, so `generate` still downloads
each client file once to compute it, as it does today.

### install.sh / install.ps1

The Fabric flow is: vanilla base version, download the Fabric profile JSON, write it to `versions/`, patch
`launcher_profiles.json`, purge server-only jars, download and verify the mod table, write tier configs, verify. For
NeoForge:

1. **Constants**: `FABRIC_MC_VERSION` / `FABRIC_LOADER_VERSION` / `FABRIC_PROFILE_URL` become `MC_VERSION=1.21.1` and
   `NEOFORGE_VERSION=21.1.265`.
2. **Loader install** (replaces "C: fabric loader profile"): download
   `https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar` and run
   ```sh
   java -jar neoforge-installer.jar --install-client "$TARGET_DIR"
   ```
   It creates `versions/neoforge-21.1.265/` and the libraries, and adds its own profile to `launcher_profiles.json`.
   Unlike the Fabric installer (which needed no Java), this needs **Java 21 on the PATH**: check `java -version` first and
   tell the player to install Temurin 21 if it is missing (the Mojang launcher's bundled runtime is Java 21 and can be
   used as a fallback).
3. **Launcher profile**: the JSON patch that sets the tier's `javaArgs` (`-Xmx3G/5G/8G`) must target the profile
   `neoforge-21.1.265` instead of `fabric-loader-1.21.11`. In TLauncher the installed version appears in the version
   list; if the profile patch fails the installer already tells the player to pick the version manually.
4. **Back up first**: before anything is deleted, copy the player's `mods/` and `config/` to
   `mods-fabric-1.21.11-backup-<date>/` and `config-fabric-1.21.11-backup-<date>/` inside the game directory. The
   current installer does not do this, and NeoForge needs a clean `mods/`.
5. **Purge**: the Fabric pack's jars must not remain. Besides the existing `purge.generated.txt` (server-only prefixes) add
   every Fabric jar prefix to `retired_prefixes` in `mods.json`: `fabric-api-`, `fabric-language-kotlin-`, and the prefix
   of each former Fabric mod (`iris-fabric-`, `sodium-fabric-`, `create-fly`, `aeronautics-fabric-`, ...). The simplest safe
   rule for this one-time move is to empty `mods/` after the backup and let step "F" download the NeoForge set. Also
   remove `config/fabric_loader_dependencies.json` and the `.fabric/` folder.
6. **Download and verify** the table as today (sha256 per file, hard fail on duplicates).
7. **Done message**: "pick `neoforge-21.1.265` in your launcher", plus the server address as before.

The installers are run as `curl ... | sh`, so keep the single-file style and keep `--manifest` for local testing.

## By hand (no installer)

1. Install Java 21. Run Minecraft 1.21.1 once from the launcher so assets download.
2. Back up your current `mods/` and `config/`.
3. `java -jar neoforge-21.1.265-installer.jar --install-client <game dir>` (macOS: `~/Library/Application Support/minecraft`,
   Linux: `~/.minecraft`, Windows: `%APPDATA%\.minecraft`).
4. Empty `mods/`, then `scripts/download-mods.sh client "<game dir>/mods"` from this repo (checks every sha512).
5. In the launcher pick `neoforge-21.1.265`, allocate 6 GB (`-Xmx6G`), start once, quit, add the server.

## TLauncher notes

- TLauncher uses the shared `.minecraft` folder, so `mods/` and `config/` are shared by every version the player has
  installed. A Fabric 1.21.11 profile and the NeoForge one cannot both keep their mods in `mods/`; jars of the wrong
  loader are ignored with errors at best and break the start at worst.
- Pick the version **`neoforge-21.1.265`** in the TLauncher version list (the NeoForge installer creates it in
  `versions/`). Do not pick plain 1.21.1; that starts without mods.
- Because the folder is shared, if a player wants to keep both worlds playable, swap the whole folder pair. The scripts
  in [`examples/tlauncher/`](../examples/tlauncher) do that on macOS: `use-backport.sh` moves `mods/` and `config/` to
  `mods-fabric-1.21.11/` and `config-fabric-1.21.11/` and copies the backport mods in; `use-fabric.sh` restores them.
  They are examples for one machine; adapt the paths on Windows or Linux.
- Multiplayer: the server must run the same NeoForge version and mods; a mismatch shows up as a "mod mismatch" screen
  at login. Offline UUIDs are unchanged (the server stays in offline mode), so existing player data still applies.

# Hosting the NeoForge 1.21.1 server

Target: the 12 GB / 4 core Proxmox VM that runs the current Fabric server. The stack stays the same
(`itzg/minecraft-server` + `lazymc` + `playit`, see [A1K2S3/minecraft](https://github.com/A1K2S3/minecraft),
`server/docker-compose.yml`); only the `mc` service and the mod list change.

## What changes in docker-compose.yml

`lazymc` and `playit` are untouched. In the `mc` service:

```yaml
  mc:
    image: itzg/minecraft-server:java21      # was: itzg/minecraft-server
    # ... volumes, networks, stop_grace_period, logging unchanged ...
    labels:
      - lazymc.enabled=true
      - lazymc.group=mc
      - lazymc.server.address=172.29.0.3:25565
      - lazymc.time.sleep_after=600
      - lazymc.server.start_timeout=900      # NeoForge + ~75 mods cold boots slower; was 600
      - lazymc.public.version=1.21.1         # was 1.21.11
      - lazymc.public.protocol=767           # was 774; 767 is the protocol of 1.21.1
    environment:
      EULA: "true"
      TYPE: "NEOFORGE"                       # was FABRIC
      VERSION: "1.21.1"                      # was 1.21.11
      NEOFORGE_VERSION: "21.1.265"           # new
      MEMORY: "8G"                           # unchanged, see below
      ONLINE_MODE: "false"                   # unchanged, see below
      MAX_TICK_TIME: "-1"
      VIEW_DISTANCE: "6"
      SIMULATION_DISTANCE: "5"
      USE_AIKAR_FLAGS: "true"
      ENABLE_RCON: "true"
      RCON_PASSWORD: "${RCON_PASSWORD}"      # keep it a reference; the value stays in the gitignored .env
      OPS: "..."                             # unchanged
      # removed: MODRINTH_DOWNLOAD_DEPENDENCIES, MODRINTH_PROJECTS_DEFAULT_VERSION_TYPE (see the mod list below)
```

`java21` is required: NeoForge 1.21.1 runs on Java 21 and the image tag selects it. Do not change `restart: "no"`
or the static `mc-net` addresses; lazymc depends on them.

## The mod list

`mods/mods.lock.json` is the source of truth: for each mod the Modrinth project and version id, filename, sha512
and side (`server`, `client`, `both`). There are 74 server-side jars (side `server` or `both`) and one datapack.

**Recommended: download the exact jars with the lock file** (checks every sha512, safe to re-run):

```sh
cd /root/mc-repo/server
mv data/mods data/mods-fabric-1.21.11 2>/dev/null     # NeoForge cannot load Fabric jars
/path/to/minecraft-1.21.1-backport/scripts/download-mods.sh server data/mods
```

and leave `MODRINTH_PROJECTS` unset, so the container does not add or replace anything. After a mod change, edit
`mods/mods.lock.json` (or regenerate it from a mod folder), commit, run the script again on the VM and restart.

Alternative that fits the friends' `scripts/generate` flow: emit `mods.generated.env` from the lock, pinned by version
id so every host gets the same jars. `MODRINTH_DOWNLOAD_DEPENDENCIES` stays off because every dependency is its own
entry in the lock:

```sh
python3 - <<'PY' > server/mods.generated.env
import json
mods = [m for m in json.load(open('mods/mods.lock.json'))['mods']
        if m['side'] in ('server', 'both') and m['type'] == 'mod']
print('MODRINTH_PROJECTS=' + ','.join('%s:%s' % (m['project'], m['version_id']) for m in mods))
PY
```

The Towers of the Wild datapack is in the lock as a datapack. The converted world already enables it and carries a
copy in `world/datapacks/`; `download-mods.sh` also drops it into `data/datapacks-to-install/`.

**Not included: Simple Voice Chat** (it was not in the converted pack; add it later as a normal `both` mod if wanted).
Chunky is included for pre-generation. `spark` is included for profiling (`/spark profiler`).

Server configs (`config/`, `defaultconfigs/`) are not in this repo. The container creates defaults on first boot. If the
old server had tuned settings for Create, Sable, C2ME or Waystones, port them by hand after the first boot (the
file names are different for the official Aeronautics/Offroad: `aeronautics-server.toml`, `offroad-server.toml`,
`simulated-server.toml`, `sable-server.toml`).

## Resources

12 GB total for the VM: `MEMORY: "8G"` leaves 4 GB for the OS, Docker, lazymc and playit. A test server with a smaller
first mod list ran on `-Xms4G -Xmx6G` and reached `Done` in about 8 seconds on an empty world; the full list with the
structure mods will boot slower and needs more heap, and no timing of it on the real VM exists yet, so keep 8 GB and watch
`spark` after the first sessions. The heap comes from `MEMORY` and `USE_AIKAR_FLAGS`; do not also hand-write
`user_jvm_args.txt`. With 4 cores, keep C2ME and do not raise `VIEW_DISTANCE` above 6-8.

Reference `server.properties` values used during testing (itzg writes these from the environment, listed so they are
not lost): `difficulty=easy` (the live server uses `DIFFICULTY=normal`), `enforce-secure-profile=false`,
`max-tick-time=60000` (the live server sets `MAX_TICK_TIME=-1`), `view-distance=10` (live: 6), `simulation-distance=10` (live: 5),
`spawn-protection=16`, `level-name=world`.

## Ports and online mode

- The public path is unchanged: players -> playit.gg tunnel -> `127.0.0.1:25565` -> lazymc -> `mc` (172.29.0.3:25565).
  `mc` publishes no port. The tunnel needs no change.
- Keep `ONLINE_MODE: "false"` and `OPS: "Name:offline"`. The converted world keeps the player data files named by the
  offline-mode UUIDs the players already join with; turning online mode on would give every player a new empty
  profile.
- Clients must run NeoForge 21.1.265 with the matching mods; a vanilla or Fabric client is rejected at login. See
  [CLIENT.md](CLIENT.md).
- While lazymc answers for a sleeping server the list shows version 1.21.1 only if you changed the two `lazymc.public.*`
  labels above.

## Pre-generating terrain with Chunky

The world was explored on Fabric with different worldgen mods in places (Terralith and BOP are versions for 1.21.1,
and the structure mods are new). Chunks that already exist keep what they have; every new chunk uses the new worldgen.
Pre-generate a ring around the explored area before players arrive so the first sessions do not stall on chunk
generation:

```sh
cd /root/mc-repo/server
docker compose stop lazymc                 # lazymc would put mc to sleep mid-run (no players)
docker compose start mc
docker compose exec mc rcon-cli "chunky world minecraft:overworld"
docker compose exec mc rcon-cli "chunky center 0 0"
docker compose exec mc rcon-cli "chunky radius 3000"
docker compose exec mc rcon-cli "chunky start"
docker compose exec mc rcon-cli "chunky progress"       # check from time to time
```

On 4 cores, expect a radius of 3000 (about 140,000 chunks) to take hours; run it overnight and use a radius that fits
the explored area. Chunky pauses and resumes (`chunky pause`, `chunky continue`); wait for it to finish, or `chunky pause`
and `save-all flush` before stopping the container. Then `docker compose up -d lazymc` again. Never pre-generate on the
only copy of the world.

## Cutover checklist

Do not do any of this before the user agrees; the plan is prepared, not started. Also need: a fresh backup that includes
the ships (see [KNOWN-GAPS.md](KNOWN-GAPS.md)).

1. **Announce downtime** to the friends (what time, how long, and that everyone has to reinstall the client afterwards;
   [CLIENT.md](CLIENT.md)).
2. **Final backup**: stop the server and `tar` the world as in [BACKPORT.md](BACKPORT.md) step 1. Keep the archive.
3. **Convert**: `./convert.sh world-final.tar.gz ~/backport-out`. Read `summary.txt`: scan errors 0, `NOT allowed: 0`,
   smoke test 0 errors, and no `sublevels:` warning unless ships are accepted as lost.
4. **Upload**: on the VM, rename, do not delete, the old world, then copy the new one in:
   ```sh
   cd /root/mc-repo/server/data
   mv world world-fabric-1.21.11
   rsync -a --info=progress2 user@workstation:backport-out/world/ ./world/
   ```
5. **Switch the stack**: edit `docker-compose.yml` as above, replace `data/mods` (see the mod list), commit, and
   `docker compose up -d` (or `./restart`).
6. **Start and verify**: join to wake lazymc (first boot takes minutes). Watch `docker compose logs -f mc` for `Done` and
   for errors mentioning `registry`, `codec` or `Failed to load`. As an op, check spawn, a base, a Create train, a
   waystone, a backpack, a turtle, and `/chunky progress`. `docker compose exec mc rcon-cli "save-all flush"`.
7. **Everyone reinstalls the client** (NeoForge 21.1.265 and the new mod list), then plays.
8. **Rollback** if step 6 fails: `docker compose down`, restore the old `docker-compose.yml`, `mv data/world data/world-failed &&
   mv data/world-fabric-1.21.11 data/world`, put the Fabric `data/mods-fabric-1.21.11` back as `data/mods`. The Fabric world
   was never modified.

Automatic backups of the VM world are currently disabled in the friends' repo. Take a manual `tar` after the first
successful session on the new server and then regularly.

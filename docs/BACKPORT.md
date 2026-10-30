# Backporting a world, step by step

The input is a backup of the Fabric 1.21.11 `world` folder. The output is a 1.21.1 world for the NeoForge server.
Nothing touches the original: `convert.sh` copies the input into `<out>/source` and makes the copy read-only.

## Requirements

| need | why |
|---|---|
| Java 21 | builds and runs Chunker; also the NeoForge 1.21.1 server |
| Python 3 | the NBT tools in `scripts/` (standard library only) |
| `curl`, `tar`, `bash` | mod and vanilla jar downloads, unpacking |
| disk: about 3x the world | the read-only source copy, the converted world, and (for the smoke test) another copy of the converted world |
| RAM: 8 GB free for Chunker | `CHUNKER_XMX=8g` is the default; lower it for small worlds |

For scale: a 7.8 GB world converts in about 5 minutes; the before/after scans and the smoke test take much longer. The
complete run on the 10-25 test backup took about 90 minutes.

## 1. Take the final backup on the VM

Do this only at cutover time (see the checklist in [HOSTING.md](HOSTING.md)). The server must be stopped so the
region files are consistent. The friends' stack is `itzg/minecraft-server` behind `lazymc`, so stop the proxy first or
a player joining would wake the server again.

```sh
cd /root/mc-repo/server            # wherever docker-compose.yml lives
docker compose exec mc rcon-cli "save-all flush"
docker compose stop lazymc playit
docker compose stop mc             # clean shutdown, saves the world
tar -czf world-final-$(date +%F).tar.gz -C data world
ls -lh world-final-*.tar.gz
```

Copy it off the VM (`scp`) and keep it: it is also the rollback point. Do not delete `data/world` on the VM until
the converted world has been verified.

The archive may contain the folder `world/` or the loose contents of it; `convert.sh` finds `level.dat` up to three
levels deep. If the world has Sable sub-levels they live in `world/sublevels/` and are included automatically.

## 2. Convert

```sh
./convert.sh world-final-2026-11-01.tar.gz ~/backport-out
```

Arguments are `<backup.tar.gz | world directory> <out directory>`. The out directory must not already contain a
`world` folder. Environment variables:

| variable | default | meaning |
|---|---|---|
| `CHUNKER_XMX` | `8g` | Chunker heap |
| `SCAN_WORKERS` | `6` | processes used by the scans |
| `BACKPORT_SETTINGS` | `backport-settings.json` | Chunker `--backport` settings |
| `SERVER_DIR` | `server-check` | template for the smoke test, `none` to skip (see step 4) |
| `SMOKE_PORT` | `25613` | port of the smoke test server |
| `BLOCKCHECK_MODS` | `.cache/server-mods` | server jars used by the block id check (downloaded on first run) |

The seven steps it prints:

1. copy or unpack the input to `<out>/source` (read-only);
2. build `chunker/cli/build/libs/chunker-cli-1.21.0.jar` if it is missing or older than the sources;
3. run Chunker: `-f JAVA_1_21_1 --backport backport-settings.json` (blocks, block entities, entities, items, player data,
   trial spawner configs, graves to chests, mod data). Logs go to `<out>/logs`;
4. `scripts/fix_world.py`: rewrite `level.dat` for 1.21.1 (data version 3955, no 1.21.11 `spawn` / `game_rules`), copy the
   Towers of the Wild datapack, carry over `sublevels/` and Sable / Aeronautics / Simulated saved data (as warnings,
   see [KNOWN-GAPS.md](KNOWN-GAPS.md));
5. scan the source (exact block counts, pre-light chunks skipped because Chunker drops them) and the result, then
   run `blockcheck.py`;
6. server smoke test (optional);
7. print and save `<out>/summary.txt`.

The converted world is `<out>/world`.

## 3. Read summary.txt

The top table compares source and converted counts:

```
                        source        converted           diff
blocks                  68831436800      68837662720       +6225920
block_entities               372809           372901            +92
entities                     238509           238509             +0
items                       1470101          1478247          +8146
players                          17               17
scan errors                       0                0
```

What should hold:

- `entities` identical (nothing lost), `players` identical, `scan errors` 0 on both sides.
- `block_entities` and `items` may grow: graves become chests, backpack backups are recovered into items.
- `blocks` grows slightly because 1.21.11 omits `block_states` for sections that only carry light data and Chunker
  writes them as explicit air sections (`scripts/airdiff.py` compares per chunk).

Below the table each category lists its largest differences, one id per line:

- `*` = explained: the id appears in the substitution tables (`BlockBackport.java`, the item log). For example
  `* biomesoplenty:red_maple_leaf_litter 969851 -> 0` pairs with `* biomesoplenty:red_maple_leaf_pile 0 -> 969851`.
  The tables are listed in [WHAT-CHANGES.md](WHAT-CHANGES.md).
- `?` = **unexplained**: the id changed count but no substitution rule or item-log line mentions it. Usually these are
  small percentage changes on common blocks (`minecraft:stone`, `minecraft:dirt`, ...) or items inside containers
  that were added by recovered backpacks. A `?` is not an error by itself. What to check:
  - a `?` for an id that **vanished** (`N -> 0`) is the one that matters: find out where it went. A mod block that does
    not exist on the 1.21.1 server shows up here (and in `blockcheck`), and would be air or missing in game.
  - a `?` with a small positive or negative delta on a vanilla id is expected noise. Counts for the 10-25 backup
    are in [KNOWN-GAPS.md](KNOWN-GAPS.md).
  - `unexplained ids: N` at the end of each block is the number of ids with `?`, not the number of problems.

Then:

- `blockcheck:` `output block ids: ... NOT allowed: 0` means every block id in the output exists on the target server
  (vanilla 1.21.1 + Vanilla Backport + the mod jars). A non-zero number lists blocks that would load as missing;
  fix the substitution table in `BlockBackport.java` and re-run.
- `conversion report:` counters from the converter, one per kind of change (boats downgraded, graves converted, variants
  reset, components dropped, ...). Lines starting with `block dropped` or `component dropped` are losses worth reading.
- `warnings (data carried over but not converted):` anything that was copied unchanged. **If this lists Sable
  sub-levels, the ships and cars in this world are not usable on 1.21.1.**
- `server smoke test:` `server log data errors (noise excluded): 0` is the target.

## 4. Server smoke test

Step 6 starts a temporary NeoForge 1.21.1 server on a copy of the result, force-loads areas around spawn and around
every player, saves, stops, and prints the data errors in the log (missing registry entries, codec
failures, corrupt block entities). It does not start unless it has a server template:

```sh
mkdir server-check
scripts/download-mods.sh server server-check/mods
curl -LO https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar
java -jar neoforge-21.1.265-installer.jar --install-server server-check
echo "eula=true" > server-check/eula.txt
printf 'online-mode=false\n' > server-check/server.properties
```

Then `./convert.sh <backup> <out>` finds `server-check/` by default (or pass `SERVER_DIR=/path`). The template is copied to
`<out>/server-test`, never modified, and uses port 25613 (change with `SMOKE_PORT`). Use a fresh `config/` or the real
server's `config/` in the template if you want the smoke test to run with your settings. `SERVER_DIR=none` skips it.

The smoke test only proves the world **loads**; it does not play the game. Before the cutover, join the converted
world once with a client (see [CLIENT.md](CLIENT.md)) and look at spawn, a base with Create machines, a waystone and a
train.

## 5. After a successful run

The result is `<out>/world`. Upload it as the server's world folder (`data/world`, `level-name=world`) as described in
[HOSTING.md](HOSTING.md). Re-running with a new backup needs a new, empty out directory.

## Re-running only part of it

The scripts are independent: `python3 scripts/fix_world.py <source world> <converted world> <warnings file>`,
`SCAN_EXACT=1 python3 scripts/scan.py <world> <out.json> <workers>`, `python3 scripts/summary.py <scan-source.json>
<scan-converted.json> <backport-items.log> <BlockBackport.java>`. `scripts/cmp.py A.json B.json [keys]` compares two scan files and
`scripts/airdiff.py <source world> <converted world>` compares air per chunk. Rebuilding the jar: `cd chunker && ./gradlew :cli:shadowJar`.

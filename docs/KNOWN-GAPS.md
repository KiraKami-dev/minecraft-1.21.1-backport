# Known gaps

**Sable sub-levels (airships, cars and other sub-level contraptions) are NOT converted yet. The 10-25 test backup had
none. A world with ships will lose them until that converter is built.**

`scripts/fix_world.py` does not drop them silently: any `sublevels/` region file with content, and any Sable /
Aeronautics / Simulated saved data (`data/sable_*`, `aeronautics_*`, `simulated_*`) with content, is copied unchanged and
listed under `warnings` in `summary.txt`. The 1.21.11 format of that data is not converted, and Sable 2.0.6 on 1.21.1
may refuse to load it or ignore it. Empty files are skipped. Before the cutover, a fresh backup with ships needs a real
sub-level converter; until then, treat a `sublevels:` warning in the summary as "ships will be missing".

Plain Create contraption entities and trains are converted (5 contraption entities, 10 train graphs and 2 trains in the
test, which round-trip through Create 6.0.10 unchanged).

## Block and item differences that are not explained

`summary.txt` marks ids it cannot explain with `?`. In the 10-25 run (`full-run2`):

- blocks: 307 ids differ, 275 unexplained. Almost all are common vanilla blocks with a small gain, for example
  `minecraft:stone` +747,477 (+0.012%), `deepslate` +632,110 (+0.009%), `netherrack` +145,389, `water` +70,639,
  `bedrock` +55,320. Their sum is a small fraction of the 68.8 billion blocks in the world; the total is +6.2 million
  (+0.009%), and about 4.3 million of that is air from sections that 1.21.11 stores without `block_states`. The remaining
  gain has not been traced to a cause. The one unexplained id that *disappears* is
  `biomesoplenty:green_flower_petal_block` (39,286 to 0): it is in the table as air, so the `?` is only because the
  summary matcher does not read the `AIR` shorthand for the green petals.
- block entities: `create:simple_kinetic` appears (+408) and pairs with `create:bracketed_kinetic` (-408), explained in
  code but shown as `?`; `DUMMY` +45 and a few +1 / +4 changes on signs, spawners, vaults, beds, hoppers are unexplained.
- items: 214 unexplained ids, all small gains (for example `minecraft:netherrack` +1,307, `cobblestone` +661). They
  come from backpack backups that were recovered into items (the converted world has 8,146 more items than the
  source) and have not been audited item by item.
- the `chunks` row of the summary drops from 1,245,268 to 878,327. The block totals do not shrink, so this counts
  something other than playable chunks (the converted world also omits pre-light chunks that Chunker drops); it has not
  been root-caused.

A `blockcheck:` line with `NOT allowed: 0` and a smoke test with 0 data errors say the world loads; they do not say every
block is where it was.

## Boats and creaking hearts

- **Boats**: all 18 boats converted (oak 10, maple 4, umbran 2, fir 1, birch 1; none lost). In the summary the birch
  boat shows as `minecraft:boat` (11 = 10 oak + 1 birch) and BOP boats as `biomesoplenty:boat` (7), because 1.21.1 uses one
  entity id per mod with a `Type`. The `?` marks next to `minecraft:boat` and `biomesoplenty:boat` are this
  renaming, not a loss.
- **Creaking hearts** are kept as blocks and block entities (Vanilla Backport adds them), but without their linked
  creaking. Whether the hearts spawn a new creaking correctly on 1.21.1 has not been checked in play.

## Other data not carried over

- Vanilla data files outside the dimensions are not converted: raids, scoreboard, forced chunks, world border. Stats
  files list items that no longer exist (harmless warnings in the log).
- Universal Graves: stored experience (7,272 xp in the test) and items beyond 54 stacks per grave are lost; the rest is
  in chests.
- Attributes that do not exist in 1.21.1 (`waypoint_transmit_range`) are removed.
- Blocks from newer vanilla versions that Vanilla Backport does not cover and that have no table entry are dropped to
  air; they are counted in the report as `block dropped (new vanilla, no equivalent)`.
- Expanded Weaponry, Saturated Eating and Extended Vanilla Endgame have no NeoForge 1.21.1 builds; see
  [WHAT-CHANGES.md](WHAT-CHANGES.md).

## Mod data saved by newer mod versions: load-tested only

The 1.21.11 mods (Create 6.0.9, Waystones, Traveler's Backpack, CC: Tweaked, Balm) save formats that the 1.21.1 builds
read differently. The converter handles every difference that was found by reading the target jars and by loading the
data, but "verified" here means **the 1.21.1 server loaded it without data errors** (the 10-25 smoke test: 0 errors,
force-loaded around spawn and all 17 players), not that someone played with it and checked it:

- **Traveler's Backpack**: contents of placed and carried backpacks and the 103 backup files; recovered items count
  as the +8,146 items.
- **Waystones**: registry, name generator, per player activations and waystone items.
- **CC: Tweaked**: computers, turtles and disks are saved by the mod itself and there is no CC-specific converter. No
  data error appeared on load, but turtle programs have not been run on the converted world.
- **Create**: train graphs, fluids, filters, tracks.

A first play session on the converted world should check one of each: open a backpack, teleport to a waystone, start a
turtle, ride a train.

## Other limits

- No real playtest of the full converted 10-25 world beyond local test servers.
- The Fabric-only client mods do not exist on NeoForge; see [CLIENT.md](CLIENT.md).
- `chunker/` keeps Chunker's own tests, which are not run by our pipeline. The only check of the build here is that
  `./gradlew :cli:shadowJar` succeeds.

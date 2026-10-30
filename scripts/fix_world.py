"""Finish a Chunker backport output: make level.dat loadable by 1.21.1, copy the datapacks Chunker leaves behind and carry over the
mod data Chunker has no converter for (Sable sub levels, Create Aeronautics / Simulated saved data): files without content are
skipped, files with content are copied unchanged (DataVersion set) and listed as a WARNING, because their format is not converted.
(waystones.dat, create_tracks.dat and the backpacks are converted by Chunker, see ModWorldData / PlayerDataConverter.)
Usage: fix_world.py SOURCE_WORLD OUT_WORLD [WARNINGS_FILE]"""
import sys, os, shutil, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

src, out = sys.argv[1:3]
warnings_file = sys.argv[3] if len(sys.argv) > 3 else None
warnings = []
root = nbt.read_file(os.path.join(out, 'level.dat'))
d = root['Data']
d['DataVersion'] = 3955
d['Version'] = {'Id': 3955, 'Name': '1.21.1', 'Series': 'main', 'Snapshot': nbt.Byte(0)}
d.pop('game_rules', None)
d.pop('WanderingTraderId', None)
spawn = d.pop('spawn', None)
if spawn:
    d['SpawnX'], d['SpawnY'], d['SpawnZ'] = [int(v) for v in spawn['pos']]
d['SpawnAngle'] = nbt.Float(spawn['yaw'] if spawn else 0.0)
d['DataPacks'] = {'Enabled': nbt.TList(['vanilla', 'mod_data', 'create:dynamic_data', 'file/Towers_Of_The_Wild_Reworked-v4.4.4+1.21-26.2-Waystones.zip'], 8),
                  'Disabled': nbt.TList(['bundle', 'trade_rebalance'], 8)}
d['ServerBrands'] = nbt.TList(['neoforge'], 8)
nbt.write_file(os.path.join(out, 'level.dat'), root)

os.makedirs(os.path.join(out, 'datapacks'), exist_ok=True)
for z in glob.glob(os.path.join(src, 'datapacks', 'Towers_Of_The_Wild*.zip')):
    shutil.copy(z, os.path.join(out, 'datapacks'))
print('level.dat patched, DataVersion', d['DataVersion'])

def has_content(v):
    if isinstance(v, dict): return any(has_content(x) for k, x in v.items() if k != 'DataVersion')
    if isinstance(v, (list, bytes, str)): return len(v) > 0
    return bool(v)

# Sable sub levels (airships, cars ...): region files of an empty world are only a zeroed header
subs = os.path.join(src, 'sublevels')
if os.path.isdir(subs):
    kept = 0
    for f in sorted(os.listdir(subs)):
        data = open(os.path.join(subs, f), 'rb').read()
        if any(data):
            os.makedirs(os.path.join(out, 'sublevels'), exist_ok=True)
            shutil.copy(os.path.join(subs, f), os.path.join(out, 'sublevels', f))
            kept += 1
    if kept:
        warnings.append(f'sublevels: {kept} Sable sub level region file(s) WITH CONTENT copied unchanged to sublevels/; the 1.21.11 format is not converted, Sable 1.21.1 may not load them (airships / cars could be missing)')
    else:
        print('sublevels: all', len(os.listdir(subs)), 'region files are empty, nothing to carry over')

# Mod saved data without a converter
for dim, label in (('', 'overworld'), ('DIM-1', 'nether'), ('DIM1', 'end')):
    folder = os.path.join(src, dim, 'data')
    if not os.path.isdir(folder): continue
    for f in sorted(os.listdir(folder)):
        if not f.endswith('.dat') or not f.startswith(('sable_', 'aeronautics_', 'simulated_')): continue
        root = nbt.read_file(os.path.join(folder, f))
        if not has_content(root.get('data', {})): continue
        dest = os.path.join(out, dim, 'data')
        os.makedirs(dest, exist_ok=True)
        root['DataVersion'] = nbt.Int(3955)
        nbt.write_file(os.path.join(dest, f), root)
        warnings.append(f'{label} data/{f}: has content, copied unchanged (format not converted)')

if warnings_file:
    with open(warnings_file, 'w') as fh:
        fh.write('\n'.join(warnings) + ('\n' if warnings else ''))
for w in warnings: print('WARNING', w)

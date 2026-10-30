"""Print forceload commands for a smoke test: around the spawn and every player (81 chunks each, clustered).
Usage: smoke_cmds.py WORLD"""
import sys, os, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt
w = sys.argv[1]
pts = []
d = nbt.read_file(os.path.join(w, 'level.dat'))['Data']
pts.append((int(d['SpawnX']), int(d['SpawnZ'])))
for p in sorted(glob.glob(os.path.join(w, 'playerdata', '*.dat'))):
    t = nbt.read_file(p)
    if str(t.get('Dimension', 'minecraft:overworld')) == 'minecraft:overworld':
        pts.append((int(t['Pos'][0]), int(t['Pos'][2])))
done = []
for x, z in pts:
    if any(abs(x - a) < 64 and abs(z - b) < 64 for a, b in done): continue
    done.append((x, z))
    print('forceload add %d %d %d %d' % (x - 64, z - 64, x + 64, z + 64))

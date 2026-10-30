"""Compare air counts per chunk between a source world and its conversion: where do the extra air blocks come from?
Usage: airdiff.py SOURCE_WORLD OUT_WORLD [region file names...]  (default: 8 mid sized regions)"""
import sys, os, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt
from scan import bits_unpack
PROTO = {'empty', 'structure_starts', 'structure_references', 'biomes', 'noise', 'surface', 'carvers', 'liquid_carvers', 'features'}

def chunks(path):
    out = {}
    for i, ts, t in nbt.read_region(path):
        out[(t['xPos'], t['zPos'])] = t
    return out

def counts(t):
    air = other = 0; ys = []
    for s in t.get('sections', []):
        ys.append(s['Y'])
        bs = s.get('block_states')
        if not bs: continue
        pal = [p['Name'] for p in bs['palette']]
        if len(pal) == 1:
            if pal[0].endswith(':air'): air += 4096
            else: other += 4096
            continue
        for idx in bits_unpack(bs.get('data', []), 4096, len(pal)):
            if pal[idx].endswith(':air'): air += 1
            else: other += 1
    return air, other, ys

src, out = sys.argv[1:3]
names = sys.argv[3:] or sorted(os.listdir(os.path.join(out, 'region')), key=lambda n: -abs(os.path.getsize(os.path.join(out, 'region', n)) - 3_000_000))[-8:]
tot = collections.Counter()
for n in names:
    a = chunks(os.path.join(src, 'region', n)); b = chunks(os.path.join(out, 'region', n))
    for pos, t in a.items():
        st = t.get('Status', 'minecraft:full').replace('minecraft:', '')
        if st in PROTO: tot['source proto chunks (not converted)'] += 1; continue
        if pos not in b: tot['source chunks missing in output'] += 1; continue
        sa, so, sy = counts(t); ba, bo, by = counts(b[pos])
        tot['chunks'] += 1; tot['air src'] += sa; tot['air out'] += ba; tot['other src'] += so; tot['other out'] += bo
        tot['sections src'] += len(sy); tot['sections out'] += len(by)
        extra = sorted(set(by) - set(sy)); lost = sorted(set(sy) - set(by))
        if extra: tot['chunks with extra sections'] += 1; tot['extra sections'] += len(extra)
        if lost: tot['chunks with lost sections'] += 1
        # extra air in sections both have
        if (ba - sa) != 4096 * len(extra) : tot['air diff not explained by extra sections'] += (ba - sa) - 4096 * len(extra)
        if bo != so: tot['chunks where non-air differs'] += 1; tot['non-air diff'] += bo - so
for k, v in tot.items(): print(f'{k}: {v}')

"""Inventory a world: block ids (exact counts), block entity ids, entity ids, item ids
(with counts, anywhere in NBT), item component keys. Usage: scan.py WORLD OUT.json [workers]"""
import sys, os, json, glob, collections
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

def bits_unpack(data, n, palen):
    bits = max(4, (palen - 1).bit_length())
    per = 64 // bits; mask = (1 << bits) - 1; out = []
    for L in data:
        L &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            out.append(L & mask); L >>= bits
            if len(out) == n: return out
    return out

def walk_items(v, C, path=''):
    """Count item stacks: compounds with string id + count (or Count)."""
    if isinstance(v, dict):
        idv = v.get('id')
        if isinstance(idv, str) and ('count' in v or 'Count' in v) and ':' in idv:
            C['items'][idv] += int(v.get('count', v.get('Count', 1)))
            comps = v.get('components')
            if isinstance(comps, dict):
                for k in comps: C['components'][k] += 1
        for k, x in v.items():
            if isinstance(x, (dict, list)): walk_items(x, C)
    elif isinstance(v, list) and not isinstance(v, (nbt.IntArray, nbt.LongArray, nbt.ByteArray)):
        for x in v:
            if isinstance(x, (dict, list)): walk_items(x, C)

def newC():
    return {k: collections.Counter() for k in ('blocks', 'block_entities', 'entities', 'items', 'components', 'errors', 'chunks')}

def scan_chunk(t, C, exact):
    secs = t.get('sections', [])
    for s in secs:
        bs = s.get('block_states')
        if not bs:
            # 1.21.11 leaves out block_states of a section without blocks (light data only): all air
            C['blocks']['minecraft:air'] += 4096; continue
        pal = bs.get('palette', [])
        names = [p['Name'] for p in pal]
        if len(pal) == 1:
            C['blocks'][names[0]] += 4096; continue
        if exact or any(not n.startswith('minecraft:') for n in names):
            for idx in bits_unpack(bs.get('data', []), 4096, len(pal)):
                if idx < len(names): C['blocks'][names[idx]] += 1
        else:
            for n in names: C['blocks'][n + ' (sections)'] += 1
    for be in t.get('block_entities', []):
        C['block_entities'][be.get('id', '?')] += 1
        walk_items(be, C)

def scan_entities(t, C):
    def ent(e):
        C['entities'][e.get('id', '?')] += 1
        for p in e.get('Passengers', []): ent(p)
    for e in t.get('Entities', []): ent(e)
    walk_items(t.get('Entities', []), C)

# Pre-light generation statuses, which Chunker does not convert (set SCAN_SKIP_PROTO=1 to skip them)
PROTO = {'empty', 'structure_starts', 'structure_references', 'biomes', 'noise', 'surface', 'carvers', 'liquid_carvers', 'features'}

def do_region(args):
    path, kind = args
    C = newC()
    try:
        for i, ts, t in nbt.read_region(path):
            if kind == 'region' and os.environ.get('SCAN_SKIP_PROTO') and t.get('Status', 'minecraft:full').replace('minecraft:', '') in PROTO:
                C['chunks']['proto skipped'] += 1; continue
            C['chunks'][kind] += 1
            if kind == 'region': scan_chunk(t, C, bool(os.environ.get('SCAN_EXACT')))
            elif kind == 'entities': scan_entities(t, C)
    except Exception as e:
        C['errors'][f'{path}: {e}'] += 1
    return C

def do_file(path):
    C = newC()
    try:
        t = nbt.read_file(path)
        walk_items(t, C)
        if 'Inventory' in t or 'EnderItems' in t:
            C['entities']['player'] += 1
            for p in [t.get('RootVehicle', {}).get('Entity')] if 'RootVehicle' in t else []:
                C['entities'][p.get('id')] += 1
    except Exception as e:
        C['errors'][f'{path}: {e}'] += 1
    return C

def merge(a, b):
    for k in a: a[k].update(b[k])

if __name__ == '__main__':
    w, out = sys.argv[1], sys.argv[2]
    nw = int(sys.argv[3]) if len(sys.argv) > 3 else 9
    jobs = []
    for dim in ['', 'DIM-1', 'DIM1']:
        for kind in ('region', 'entities'):
            for p in glob.glob(os.path.join(w, dim, kind, '*.mca')):
                if os.path.getsize(p) > 0: jobs.append((p, kind))
    files = glob.glob(os.path.join(w, 'playerdata', '*.dat'))
    files += [p for p in glob.glob(os.path.join(w, 'backpacks', '**', '*.dat'), recursive=True)]
    files += glob.glob(os.path.join(w, 'data', '*.dat')) + glob.glob(os.path.join(w, 'DIM*', 'data', '*.dat'))
    if os.environ.get('SCAN_NO_GRAVES'):  # the grave save data becomes chests, so it isn't a like for like comparison
        files = [f for f in files if os.path.basename(f) != 'universal-graves.dat']
    jobs.sort(key=lambda j: -os.path.getsize(j[0]))
    T = newC()
    with Pool(nw) as pool:
        for n, C in enumerate(pool.imap_unordered(do_region, jobs, chunksize=1)):
            merge(T, C)
            if n % 200 == 0: print(n, '/', len(jobs), flush=True)
        for C in pool.imap_unordered(do_file, files):
            merge(T, C)
    json.dump({k: dict(v.most_common()) for k, v in T.items()}, open(out, 'w'), indent=1)
    print('done', {k: len(v) for k, v in T.items()})

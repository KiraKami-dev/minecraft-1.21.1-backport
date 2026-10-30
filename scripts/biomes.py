"""Biome palette inventory (section counts). Usage: biomes.py WORLD [OUT.json] [workers]"""
import sys, os, glob, json, collections
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

def do_region(path):
    C = collections.Counter()
    try:
        for i, ts, t in nbt.read_region(path):
            for s in t.get('sections', []):
                for b in s.get('biomes', {}).get('palette', []): C[b] += 1
    except Exception as e:
        C['ERR ' + path + ': ' + str(e)] += 1
    return C

if __name__ == '__main__':
    w = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else None
    nw = int(sys.argv[3]) if len(sys.argv) > 3 else 6
    files = [p for d in ['', 'DIM-1', 'DIM1'] for p in glob.glob(os.path.join(w, d, 'region', '*.mca')) if os.path.getsize(p) > 0]
    T = collections.Counter()
    with Pool(nw) as pool:
        for C in pool.imap_unordered(do_region, files, chunksize=1): T.update(C)
    res = dict(T.most_common())
    if out: json.dump(res, open(out, 'w'), indent=1)
    else: print(json.dumps(res, indent=1))

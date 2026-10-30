"""Check a converted world's block ids against what the target server can load.

Usage: blockcheck.py OUT_WORLD [--in IN_WORLD --java BlockBackport.java] [--workers N]

1. Lists every block id in OUT_WORLD (chunk palettes) that is not in the allowed set:
   vanilla 1.21.1 + Vanilla Backport + the server mod jars (blockstates + lang block keys). The jars are read from
   $BLOCKCHECK_MODS (default .cache/server-mods, filled by scripts/download-mods.sh server); the vanilla 1.21.1
   client jar is downloaded from Mojang into .cache/ on first use.
2. With --in/--java: counts blocks exactly (in the sections that matter), applies the substitution table parsed
   from BlockBackport.java to the IN counts and compares with the OUT counts.
3. Flags VB block property values outside the domain given by the VB blockstate files.
"""
import sys, os, re, json, glob, zipfile, collections, argparse
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, '.cache')
IDS = os.environ.get('BLOCKCHECK_MODS', os.path.join(CACHE, 'server-mods'))
VB_JAR = (glob.glob(os.path.join(IDS, 'VanillaBackport*.jar')) or [os.path.join(IDS, 'VanillaBackport.jar')])[0]
VANILLA_JAR = os.path.join(CACHE, 'vanilla-1.21.1-client.jar')


def vanilla_jar():
    if not os.path.exists(VANILLA_JAR):
        import subprocess
        get = lambda u: json.loads(subprocess.run(['curl', '-sfL', u], capture_output=True, check=True).stdout)
        ver = next(v for v in get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')['versions'] if v['id'] == '1.21.1')
        url = get(ver['url'])['downloads']['client']['url']
        os.makedirs(CACHE, exist_ok=True)
        subprocess.run(['curl', '-sfL', '-o', VANILLA_JAR, url], check=True)
    return VANILLA_JAR
TECH = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}


def jar_blocks(path):
    z = zipfile.ZipFile(path)
    out = set()
    for n in z.namelist():
        m = re.match(r'assets/([^/]+)/blockstates/(.+)\.json$', n)
        if m: out.add(m.group(1) + ':' + m.group(2))
        m = re.match(r'assets/([^/]+)/lang/en_us\.json$', n)
        if m:
            try:
                for k in json.loads(z.read(n)):
                    p = k.split('.')
                    if len(p) == 3 and p[0] == 'block': out.add(p[1] + ':' + p[2])
            except Exception:
                pass
    return out


def vb_domains():
    """property -> allowed values per VB block, from the blockstate files (what can be determined)."""
    z = zipfile.ZipFile(VB_JAR)
    dom = {}
    for n in z.namelist():
        m = re.match(r'assets/minecraft/blockstates/(.+)\.json$', n)
        if not m: continue
        j = json.loads(z.read(n)); props = {}
        for k in j.get('variants', {}):
            for kv in k.split(','):
                if '=' in kv:
                    a, b = kv.split('=', 1); props.setdefault(a, set()).add(b)
        for part in j.get('multipart', []):
            w = part.get('when', {})
            conds = w.get('OR', []) + w.get('AND', []) if ('OR' in w or 'AND' in w) else [w]
            for c in conds:
                for a, b in c.items(): props.setdefault(a, set()).update(str(b).split('|'))
        dom['minecraft:' + m.group(1)] = props
    return dom


def parse_table(java_file):
    """Parse BlockBackport.java: rule(from,to,keep) / petals(colour,to) / the grave rule. -> {from: to}."""
    src = open(java_file).read()
    consts = {'AIR': 'minecraft:air'}
    table = {}
    for m in re.finditer(r'^\s*rule\("([^"]+)",\s*("[^"]+"|AIR),', src, re.M):
        to = consts['AIR'] if m.group(2) == 'AIR' else m.group(2).strip('"')
        table[m.group(1)] = to
    for m in re.finditer(r'^\s*petals\("([^"]+)",\s*("[^"]+"|AIR)\)', src, re.M):
        to = consts['AIR'] if m.group(2) == 'AIR' else m.group(2).strip('"')
        table['biomesoplenty:%s_flower_petal_block' % m.group(1)] = to
    if 'MAP.put("universal_graves:grave"' in src: table['universal_graves:grave'] = 'minecraft:chest'
    return table


def unpack(data, n, palen):
    bits = max(4, (palen - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1; out = []
    for L in data:
        L &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            out.append(L & mask); L >>= bits
            if len(out) == n: return out
    return out


def do_region(args):
    path, interesting = args
    presence = collections.Counter(); exact = collections.Counter(); props = collections.Counter(); errs = []
    try:
        for i, ts, t in nbt.read_region(path):
            for s in t.get('sections', []):
                bs = s.get('block_states')
                if not bs: continue
                pal = bs.get('palette', [])
                for e in pal:
                    n = e['Name']; presence[n] += 1
                    props[(n, json.dumps(dict(e.get('Properties', {})), sort_keys=True))] += 1
                names = [e['Name'] for e in pal]
                if interesting and any(n in interesting for n in names):
                    if len(pal) == 1: exact[names[0]] += 4096
                    else:
                        for idx in unpack(bs.get('data', []), 4096, len(pal)):
                            if idx < len(names): exact[names[idx]] += 1
    except Exception as e:
        errs.append('%s: %s' % (path, e))
    return presence, exact, props, errs


def scan(world, interesting, workers):
    files = [p for d in ['', 'DIM-1', 'DIM1'] for p in glob.glob(os.path.join(world, d, 'region', '*.mca')) if os.path.getsize(p) > 0]
    P = collections.Counter(); X = collections.Counter(); PR = collections.Counter(); E = []
    with Pool(workers) as pool:
        for p, x, pr, e in pool.imap_unordered(do_region, [(f, interesting) for f in files]):
            P.update(p); X.update(x); PR.update(pr); E += e
    return P, X, PR, E


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out'); ap.add_argument('--in', dest='inw'); ap.add_argument('--java'); ap.add_argument('--workers', type=int, default=8)
    a = ap.parse_args()

    allowed = set(TECH)
    for j in glob.glob(os.path.join(IDS, '*.jar')) + [VB_JAR, vanilla_jar()]: allowed |= jar_blocks(j)
    table = parse_table(a.java) if a.java else {}
    # Vanilla Backport blocks that vanilla 1.21.1 lacks must pass through unchanged
    vb_new = jar_blocks(VB_JAR) - jar_blocks(vanilla_jar())
    interesting = set(table) | set(table.values()) | {b for b in vb_new if b.startswith('minecraft:')}

    P, X, PR, E = scan(a.out, interesting, a.workers)
    for e in E: print('ERROR', e)
    bad = {k: v for k, v in P.items() if k not in allowed}
    print('output block ids: %d distinct, NOT allowed: %d' % (len(P), len(bad)))
    for k, v in sorted(bad.items(), key=lambda x: -x[1]): print('  NOT ALLOWED', k, '(palette entries: %d)' % v)

    dom = vb_domains(); badprop = collections.Counter()
    for (n, pj), c in PR.items():
        d = dom.get(n)
        if not d: continue
        for k, v in json.loads(pj).items():
            if k in d and str(v) not in d[k]: badprop[(n, k, v)] += c
    print('VB property values outside blockstate domain: %d' % len(badprop))
    for k, v in badprop.most_common(20): print('  ', k, v)

    if a.inw:
        _, XI, _, EI = scan(a.inw, interesting, a.workers)
        expected = collections.Counter()
        for k, v in XI.items():
            expected[table.get(k, k)] += v
        print('\nsubstitution check (exact counts, in -> out; expected = in with table applied)')
        ok = True
        for s in sorted(table):
            if XI[s] or X[s]:
                print('  %-52s -> %-36s in %7d  still-in-out %d' % (s, table[s], XI[s], X[s] if table[s] != s else 0))
                if X[s] and table[s] != s: ok = False
        for k in sorted(interesting):
            if k in table: continue
            diff = X[k] - expected[k]
            if k in TECH and diff > 0 and diff % 4096 == 0:
                print('  note: %s +%d (Chunker adds empty sections, also in stock output)' % (k, diff)); continue
            if diff:
                ok = False; print('  MISMATCH %-40s expected %d got %d' % (k, expected[k], X[k]))
        print('substitution counts match table:', ok)


if __name__ == '__main__':
    main()

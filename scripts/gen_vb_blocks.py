"""Write chunker's vb-blocks.txt: Vanilla Backport block ids = vb-ids.json blocks + blockstates in the jar
(vb-ids.json misses wall variants such as copper_wall_torch) that vanilla 1.21.1 lacks."""
import zipfile, re, json, sys
vb_json, vb_jar, van_jar, out = sys.argv[1:5]
def states(p, ns='minecraft'):
    return {ns + ':' + m.group(1) for n in zipfile.ZipFile(p).namelist()
            if (m := re.match(r'assets/minecraft/blockstates/(.+)\.json$', n))}
ids = set(json.load(open(vb_json))['block']) | (states(vb_jar) - states(van_jar))
open(out, 'w').write('\n'.join(sorted(ids)) + '\n')
print(len(ids), 'ids')

import json, sys, subprocess, urllib.parse
mods = json.load(open(sys.argv[1]))['mods']
def get(u):
    return json.loads(subprocess.run(['curl','-s',u],capture_output=True).stdout)
q=urllib.parse.quote(json.dumps(['neoforge']));g=urllib.parse.quote(json.dumps(['1.21.1']))
for m in mods:
    if m['side']=='client': continue
    s=m['slug']
    try:
        vs=get(f"https://api.modrinth.com/v2/project/{s}/version?loaders={q}&game_versions={g}")
        best = vs[0]['version_number']+' '+vs[0]['id'] if vs else '-'
    except Exception as e:
        best='ERR '+str(e)
    print(f"{s}\t{m['side']}\t{m['type']}\t{best}")

import sys,os,glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt
from peek import show
want=sys.argv[2]; n=int(sys.argv[3]) if len(sys.argv)>3 else 1
found=0
for p in sorted(glob.glob(sys.argv[1]+'/region/*.mca'), key=os.path.getsize):
    for i,ts,t in nbt.read_region(p):
        for be in t.get('block_entities',[]):
            if be.get('id')==want:
                print(p, i); show(be, maxl=8, depth=8); found+=1
                if found>=n: sys.exit()

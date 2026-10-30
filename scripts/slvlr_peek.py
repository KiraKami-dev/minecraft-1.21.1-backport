import sys, struct, gzip, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt
tot=0
for p in sys.argv[1:]:
    b=open(p,'rb').read()
    for i in range(1024):
        span=struct.unpack('>i',b[i*4:i*4+4])[0]
        if not span: continue
        start=span>>8 if True else 0
        # pack format unknown: try both
        print(p.split('/')[-1], i, hex(span), len(b))
        tot+=1
print(tot)

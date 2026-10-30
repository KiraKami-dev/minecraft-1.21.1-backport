import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); import nbt
def show(v, ind=0, maxl=6, depth=6):
    pad='  '*ind
    if depth==0: return print(pad+'...')
    if isinstance(v, dict):
        for k,x in v.items():
            if isinstance(x,(dict,list)) and not isinstance(x,(nbt.IntArray,nbt.LongArray,nbt.ByteArray)):
                print(pad+f'{k}: <{type(x).__name__} {len(x)}>'); 
                show(x, ind+1, maxl, depth-1)
            else:
                s=repr(x); print(pad+f'{k}: {type(x).__name__} {s[:100]}')
    elif isinstance(v, list) and not isinstance(v,(nbt.IntArray,nbt.LongArray)):
        for x in v[:maxl]:
            print(pad+'-'); show(x, ind+1, maxl, depth-1) if isinstance(x,(dict,list)) else print(pad+'  '+repr(x)[:100])
        if len(v)>maxl: print(pad+f'... +{len(v)-maxl}')
import os
if __name__=='__main__':
  p=sys.argv[2]
  if p.endswith('.mca'):
      want=int(sys.argv[3]) if len(sys.argv)>3 and sys.argv[3] else None
      for i,ts,t in nbt.read_region(p):
          if want is None or i==want: show(t, maxl=int(sys.argv[4]) if len(sys.argv)>4 else 4); break
  else:
      show(nbt.read_file(p))
  
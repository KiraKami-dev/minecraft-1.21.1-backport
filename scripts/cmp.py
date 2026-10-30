import json,sys
a=json.load(open(sys.argv[1]));b=json.load(open(sys.argv[2]))
keys=sys.argv[3].split(',') if len(sys.argv)>3 else a.keys()
for k in keys:
  A,B=a[k],b.get(k,{})
  print('==',k,'sumA',sum(A.values()),'sumB',sum(B.values()))
  for x in sorted(set(A)|set(B), key=lambda x:-abs(A.get(x,0)-B.get(x,0))):
    if A.get(x,0)!=B.get(x,0): print(f'   {x}: {A.get(x,0)} -> {B.get(x,0)}')

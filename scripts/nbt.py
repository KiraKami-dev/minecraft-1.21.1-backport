"""Minimal NBT + Anvil region reader/writer that keeps exact tag types.

Values: Byte/Short/Int/Long/Float/Double are wrapped in typed classes so a round
trip writes the same tag ids. Compound = dict (insertion ordered), List = TList.
"""
import struct, zlib, gzip, io, os

class Byte(int): tid = 1
class Short(int): tid = 2
class Int(int): tid = 3
class Long(int): tid = 4
class Float(float): tid = 5
class Double(float): tid = 6
class ByteArray(bytes): tid = 7
class String(str): tid = 8
class TList(list):
    tid = 9
    def __init__(self, it=(), etype=0):
        super().__init__(it); self.etype = etype
class Compound(dict): tid = 10
class IntArray(list): tid = 11
class LongArray(list): tid = 12

def tid_of(v):
    if isinstance(v, (Byte, Short, Int, Long, Float, Double, ByteArray, TList, IntArray, LongArray)):
        return v.tid
    if isinstance(v, dict): return 10
    if isinstance(v, str): return 8
    if isinstance(v, bool): return 1
    if isinstance(v, int): return 3
    if isinstance(v, float): return 6
    if isinstance(v, list): return 9
    raise TypeError(type(v))

class _R:
    __slots__ = ('b', 'p')
    def __init__(self, b): self.b = b; self.p = 0
    def take(self, n):
        p = self.p; self.p = p + n; return self.b[p:p + n]

def _rs(r):
    n, = struct.unpack('>H', r.take(2))
    return r.take(n).decode('utf-8', 'surrogateescape')

def _read(r, t):
    if t == 1: return Byte(struct.unpack('>b', r.take(1))[0])
    if t == 2: return Short(struct.unpack('>h', r.take(2))[0])
    if t == 3: return Int(struct.unpack('>i', r.take(4))[0])
    if t == 4: return Long(struct.unpack('>q', r.take(8))[0])
    if t == 5: return Float(struct.unpack('>f', r.take(4))[0])
    if t == 6: return Double(struct.unpack('>d', r.take(8))[0])
    if t == 7:
        n, = struct.unpack('>i', r.take(4)); return ByteArray(r.take(n))
    if t == 8: return String(_rs(r))
    if t == 9:
        et, n = struct.unpack('>bi', r.take(5))
        return TList([_read(r, et) for _ in range(n)], et)
    if t == 10:
        d = Compound()
        while True:
            ct = r.take(1)[0]
            if ct == 0: return d
            k = _rs(r); d[k] = _read(r, ct)
    if t == 11:
        n, = struct.unpack('>i', r.take(4)); return IntArray(struct.unpack('>%di' % n, r.take(4 * n)))
    if t == 12:
        n, = struct.unpack('>i', r.take(4)); return LongArray(struct.unpack('>%dq' % n, r.take(8 * n)))
    raise ValueError('bad tag %d at %d' % (t, r.p))

def loads(b):
    r = _R(b); t = r.take(1)[0]
    if t == 0: return None
    _rs(r); return _read(r, t)

def _ws(o, s):
    e = s.encode('utf-8', 'surrogateescape'); o.append(struct.pack('>H', len(e))); o.append(e)

def _write(o, t, v):
    if t == 1: o.append(struct.pack('>b', int(v)))
    elif t == 2: o.append(struct.pack('>h', v))
    elif t == 3: o.append(struct.pack('>i', v))
    elif t == 4: o.append(struct.pack('>q', v))
    elif t == 5: o.append(struct.pack('>f', v))
    elif t == 6: o.append(struct.pack('>d', v))
    elif t == 7: o.append(struct.pack('>i', len(v))); o.append(bytes(v))
    elif t == 8: _ws(o, v)
    elif t == 9:
        et = getattr(v, 'etype', 0)
        if v and (et == 0 or not isinstance(v, TList)): et = tid_of(v[0])
        if not v: et = et if isinstance(v, TList) else 0
        o.append(struct.pack('>bi', et, len(v)))
        for x in v: _write(o, et, x)
    elif t == 10:
        for k, x in v.items():
            ct = tid_of(x); o.append(bytes([ct])); _ws(o, k); _write(o, ct, x)
        o.append(b'\x00')
    elif t == 11: o.append(struct.pack('>i%di' % len(v), len(v), *v))
    elif t == 12: o.append(struct.pack('>i%dq' % len(v), len(v), *v))
    else: raise ValueError(t)

def dumps(v, name=''):
    o = []; t = tid_of(v); o.append(bytes([t])); _ws(o, name); _write(o, t, v)
    return b''.join(o)

def read_file(path):
    with open(path, 'rb') as f: b = f.read()
    if b[:2] == b'\x1f\x8b': b = gzip.decompress(b)
    return loads(b)

def write_file(path, v, gz=True):
    b = dumps(v)
    if gz: b = gzip.compress(b, 6, mtime=0)
    with open(path, 'wb') as f: f.write(b)

# ---- Anvil region files ----
def read_region(path):
    """Yields (index, timestamp, Compound) for every chunk."""
    with open(path, 'rb') as f: data = f.read()
    if len(data) < 8192: return
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], 'big'); cnt = data[i * 4 + 3]
        if off == 0 or cnt == 0: continue
        ts, = struct.unpack('>I', data[4096 + i * 4:4096 + i * 4 + 4])
        p = off * 4096
        ln, = struct.unpack('>I', data[p:p + 4]); comp = data[p + 4]
        raw = data[p + 5:p + 4 + ln]
        if comp & 0x80:  # external .mcc
            raise ValueError('external chunk in %s idx %d' % (path, i))
        if comp == 2: raw = zlib.decompress(raw)
        elif comp == 1: raw = gzip.decompress(raw)
        elif comp == 3: pass
        elif comp == 4:
            import lz4.block  # not expected
            raw = lz4.block.decompress(raw)
        yield i, ts, loads(raw)

def write_region(path, chunks):
    """chunks: iterable of (index, timestamp, Compound)."""
    header = bytearray(8192); body = []; sector = 2
    for i, ts, tag in chunks:
        raw = zlib.compress(dumps(tag), 6)
        blob = struct.pack('>IB', len(raw) + 1, 2) + raw
        pad = (-len(blob)) % 4096; blob += b'\x00' * pad
        n = len(blob) // 4096
        if n > 255: raise ValueError('chunk too big %s %d' % (path, i))
        header[i * 4:i * 4 + 4] = (sector << 8 | n).to_bytes(4, 'big')
        header[4096 + i * 4:4096 + i * 4 + 4] = struct.pack('>I', ts)
        body.append(blob); sector += n
    if sector == 2:
        return False
    tmp = path + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(header); [f.write(b) for b in body]
    os.replace(tmp, path)
    return True

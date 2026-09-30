"""
Minimal NBT reader/writer (big-endian, gzip) for structure templates.

Values map to Python types with explicit tag wrappers where the type would be ambiguous:
    Byte(v), Short(v), Int(v) / plain int, Long(v), Float(v), Double(v) / plain float,
    str, List(tag_type, items), dict (compound), IntArray([...]), LongArray([...]), ByteArray(bytes)
"""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class _Num:
    __slots__ = ("v",)

    def __init__(self, v):
        self.v = v

    def __repr__(self):
        return f"{type(self).__name__}({self.v!r})"

    def __eq__(self, other):
        return type(self) is type(other) and self.v == other.v

    def __hash__(self):
        return hash((type(self), self.v))


class Byte(_Num):
    pass


class Short(_Num):
    pass


class Int(_Num):
    pass


class Long(_Num):
    pass


class Float(_Num):
    pass


class Double(_Num):
    pass


class List:
    def __init__(self, tag, items):
        self.tag, self.items = tag, list(items)

    def __repr__(self):
        return f"List({self.tag}, {self.items!r})"


class IntArray(list):
    pass


class LongArray(list):
    pass


class ByteArray(bytes):
    pass


def _tag_of(v):
    if isinstance(v, bool):
        return BYTE
    for cls, tag in ((Byte, BYTE), (Short, SHORT), (Int, INT), (Long, LONG), (Float, FLOAT), (Double, DOUBLE)):
        if isinstance(v, cls):
            return tag
    if isinstance(v, int):
        return INT
    if isinstance(v, float):
        return DOUBLE
    if isinstance(v, str):
        return STRING
    if isinstance(v, List):
        return LIST
    if isinstance(v, dict):
        return COMPOUND
    if isinstance(v, IntArray):
        return INT_ARRAY
    if isinstance(v, LongArray):
        return LONG_ARRAY
    if isinstance(v, (ByteArray, bytes)):
        return BYTE_ARRAY
    raise TypeError(f"cannot encode {type(v)}: {v!r}")


# ------------------------------------------------------------------ writing
def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(tag, v):
    raw = v.v if isinstance(v, _Num) else v
    if tag == BYTE:
        return struct.pack(">b", int(raw))
    if tag == SHORT:
        return struct.pack(">h", raw)
    if tag == INT:
        return struct.pack(">i", raw)
    if tag == LONG:
        return struct.pack(">q", raw)
    if tag == FLOAT:
        return struct.pack(">f", raw)
    if tag == DOUBLE:
        return struct.pack(">d", raw)
    if tag == STRING:
        return _str(v)
    if tag == BYTE_ARRAY:
        return struct.pack(">i", len(v)) + bytes(v)
    if tag == INT_ARRAY:
        return struct.pack(">i", len(v)) + b"".join(struct.pack(">i", x) for x in v)
    if tag == LONG_ARRAY:
        return struct.pack(">i", len(v)) + b"".join(struct.pack(">q", x) for x in v)
    if tag == LIST:
        return bytes([v.tag if v.items else END]) + struct.pack(">i", len(v.items)) + b"".join(_payload(v.tag, x) for x in v.items)
    if tag == COMPOUND:
        out = b""
        for k, x in v.items():
            t = _tag_of(x)
            out += bytes([t]) + _str(k) + _payload(t, x)
        return out + bytes([END])
    raise ValueError(tag)


def write(path, root):
    data = bytes([COMPOUND]) + _str("") + _payload(COMPOUND, root)
    with gzip.open(path, "wb") as f:
        f.write(data)


# ------------------------------------------------------------------ reading
def _read(tag, f):
    r = lambda fmt: struct.unpack(fmt, f.read(struct.calcsize(fmt)))[0]
    if tag == BYTE:
        return Byte(r(">b"))
    if tag == SHORT:
        return Short(r(">h"))
    if tag == INT:
        return r(">i")
    if tag == LONG:
        return Long(r(">q"))
    if tag == FLOAT:
        return Float(r(">f"))
    if tag == DOUBLE:
        return r(">d")
    if tag == STRING:
        return f.read(r(">H")).decode("utf-8")
    if tag == BYTE_ARRAY:
        return ByteArray(f.read(r(">i")))
    if tag == INT_ARRAY:
        return IntArray(r(">i") for _ in range(r(">i")))
    if tag == LONG_ARRAY:
        return LongArray(r(">q") for _ in range(r(">i")))
    if tag == LIST:
        t = r(">b")
        n = r(">i")
        return List(t, [_read(t, f) for _ in range(n)])
    if tag == COMPOUND:
        out = {}
        while True:
            t = r(">b")
            if t == END:
                return out
            k = f.read(r(">H")).decode("utf-8")
            out[k] = _read(t, f)
    raise ValueError(tag)


def read(path_or_bytes):
    data = path_or_bytes if isinstance(path_or_bytes, (bytes, bytearray)) else open(path_or_bytes, "rb").read()
    try:
        data = gzip.decompress(data)
    except OSError:
        pass
    f = io.BytesIO(data)
    assert f.read(1)[0] == COMPOUND
    f.read(struct.unpack(">H", f.read(2))[0])
    return _read(COMPOUND, f)

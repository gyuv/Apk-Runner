"""Minimal DEX reader: classes, method references and Android API usage."""
import struct
from collections import Counter


def _uleb(buf, off):
    result = shift = 0
    while True:
        b = buf[off]
        off += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, off
        shift += 7


class Dex:
    def __init__(self, buf):
        if buf[:4] != b"dex\n":
            raise ValueError("not a DEX file")
        self.buf = buf
        self.version = buf[4:7].decode()
        (s_size, s_off, t_size, t_off, _, _, _, _, m_size, m_off,
         c_size, c_off) = struct.unpack_from("<12I", buf, 0x38)
        self._s_off = struct.unpack_from("<%dI" % s_size, buf, s_off)
        self._types = struct.unpack_from("<%dI" % t_size, buf, t_off)
        self._methods = [struct.unpack_from("<HHI", buf, m_off + 8 * i) for i in range(m_size)]
        self._classes = [struct.unpack_from("<I", buf, c_off + 32 * i)[0] for i in range(c_size)]
        self._cache = {}

    def string(self, idx):
        s = self._cache.get(idx)
        if s is None:
            _, p = _uleb(self.buf, self._s_off[idx])
            end = self.buf.index(b"\0", p)
            s = self._cache[idx] = self.buf[p:end].decode("utf-8", "replace")  # MUTF-8, close enough
        return s

    def type(self, idx):
        return self.string(self._types[idx])

    def classes(self):
        return [_java_name(self.type(t)) for t in self._classes]

    def method_refs(self):
        """Yield 'pkg.Class.method' for every method referenced by the DEX."""
        for cls, _, name in self._methods:
            yield "%s.%s" % (_java_name(self.type(cls)), self.string(name))


def _java_name(desc):
    return desc[1:-1].replace("/", ".") if desc.startswith("L") else desc


FRAMEWORK_PREFIXES = ("android.", "androidx.", "com.google.android.")


def api_usage(dex_files):
    """Count framework API calls across DEX files, excluding classes the app defines."""
    dexes = [Dex(d) for d in dex_files]
    own = set()
    for d in dexes:
        own.update(d.classes())
    calls = Counter()
    for d in dexes:
        for ref in d.method_refs():
            cls = ref.rsplit(".", 1)[0]
            if cls not in own and ref.startswith(FRAMEWORK_PREFIXES):
                calls[ref] += 1
    return len(own), calls

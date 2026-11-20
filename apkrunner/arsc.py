"""Minimal resources.arsc reader: resolve an app's label and launcher icon offline."""
import struct

from .axml import _string_pool

RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
TYPE_REFERENCE, TYPE_STRING = 0x01, 0x03
FLAG_SPARSE, FLAG_OFFSET16 = 0x01, 0x02
ENTRY_COMPLEX, ENTRY_COMPACT = 0x0001, 0x0008
NO_ENTRY = 0xFFFFFFFF


class Table:
    def __init__(self, buf):
        self.strings = []
        self.entries = {}  # resid -> [(config_dict, dtype, data)]
        hsize = struct.unpack_from("<H", buf, 2)[0]
        off = hsize
        while off + 8 <= len(buf):
            ctype, _, csize = struct.unpack_from("<HHI", buf, off)
            if csize == 0:
                break
            if ctype == 0x0001:
                self.strings = _string_pool(buf, off)
            elif ctype == RES_TABLE_PACKAGE:
                self._package(buf, off, csize)
            off += csize

    def _package(self, buf, base, size):
        hsize = struct.unpack_from("<H", buf, base + 2)[0]
        pkg_id = struct.unpack_from("<I", buf, base + 8)[0]
        off = base + hsize
        while off + 8 <= base + size:
            ctype, _, csize = struct.unpack_from("<HHI", buf, off)
            if csize == 0:
                break
            if ctype == RES_TABLE_TYPE:
                self._type(buf, off, pkg_id)
            off += csize

    def _type(self, buf, base, pkg_id):
        hsize = struct.unpack_from("<H", buf, base + 2)[0]
        type_id, flags = buf[base + 8], buf[base + 9]
        count, start = struct.unpack_from("<II", buf, base + 12)
        cfg = base + 20
        config = {"lang": buf[cfg + 8:cfg + 10].rstrip(b"\0").decode("latin-1"),
                  "density": struct.unpack_from("<H", buf, cfg + 14)[0]}
        idx = base + hsize
        if flags & FLAG_SPARSE:
            pairs = [struct.unpack_from("<HH", buf, idx + 4 * i) for i in range(count)]
            slots = [(e, o * 4) for e, o in pairs]
        elif flags & FLAG_OFFSET16:
            raw = struct.unpack_from("<%dH" % count, buf, idx)
            slots = [(i, o * 4) for i, o in enumerate(raw) if o != 0xFFFF]
        else:
            raw = struct.unpack_from("<%dI" % count, buf, idx)
            slots = [(i, o) for i, o in enumerate(raw) if o != NO_ENTRY]
        for entry, o in slots:
            p = base + start + o
            esize, eflags = struct.unpack_from("<HH", buf, p)
            if eflags & ENTRY_COMPACT:
                dtype, data = eflags >> 8, struct.unpack_from("<I", buf, p + 4)[0]
            elif eflags & ENTRY_COMPLEX:
                continue
            else:
                dtype, data = buf[p + esize + 3], struct.unpack_from("<I", buf, p + esize + 4)[0]
            resid = (pkg_id << 24) | (type_id << 16) | entry
            self.entries.setdefault(resid, []).append((config, dtype, data))

    def values(self, resid, depth=0):
        """All (config, value) for resid, following references; strings decoded."""
        out = []
        for config, dtype, data in self.entries.get(resid, []):
            if dtype == TYPE_REFERENCE and depth < 8:
                out += self.values(data, depth + 1)
            elif dtype == TYPE_STRING and data < len(self.strings):
                out.append((config, self.strings[data]))
        return out

    def string(self, resid):
        vals = self.values(resid)
        for config, v in vals:
            if not config["lang"]:
                return v
        for config, v in vals:
            if config["lang"] == "en":
                return v
        return vals[0][1] if vals else None

    def files(self, resid):
        """File paths for resid, best density first (anydpi/xml last)."""
        vals = self.values(resid)
        rank = lambda cv: (cv[1].endswith(".xml"), -(cv[0]["density"] if cv[0]["density"] < 0xFFFE else 0))
        return [v for _, v in sorted(vals, key=rank)]


def ref_id(value):
    """'@0x7f0a0001' (as produced by axml.parse) -> int, else None."""
    if isinstance(value, str) and value.startswith("@0x"):
        return int(value[3:], 16)
    return None

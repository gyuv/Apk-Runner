"""Minimal parser for Android binary XML (AndroidManifest.xml inside an APK)."""
import struct

RES_STRING_POOL = 0x0001
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
UTF8_FLAG = 1 << 8
TYPE_STRING, TYPE_INT_DEC, TYPE_INT_HEX, TYPE_BOOL = 0x03, 0x10, 0x11, 0x12


def _len8(buf, off):
    n = buf[off]
    if n & 0x80:
        return ((n & 0x7F) << 8) | buf[off + 1], off + 2
    return n, off + 1


def _len16(buf, off):
    n = struct.unpack_from("<H", buf, off)[0]
    if n & 0x8000:
        return ((n & 0x7FFF) << 16) | struct.unpack_from("<H", buf, off + 2)[0], off + 4
    return n, off + 2


def _string_pool(buf, base):
    _, hsize, _ = struct.unpack_from("<HHI", buf, base)
    count, _, flags, str_start, _ = struct.unpack_from("<IIIII", buf, base + 8)
    offsets = struct.unpack_from("<%dI" % count, buf, base + hsize)
    data = base + str_start
    out = []
    for o in offsets:
        p = data + o
        if flags & UTF8_FLAG:
            _, p = _len8(buf, p)
            n, p = _len8(buf, p)
            out.append(buf[p:p + n].decode("utf-8", "replace"))
        else:
            n, p = _len16(buf, p)
            out.append(buf[p:p + 2 * n].decode("utf-16-le", "replace"))
    return out


def parse(buf):
    """Return a list of (depth, tag, {attr: value}) for every element."""
    strings, elements, depth = [], [], 0
    off = struct.unpack_from("<HHI", buf, 0)[1]
    while off + 8 <= len(buf):
        ctype, _, csize = struct.unpack_from("<HHI", buf, off)
        if csize == 0:
            break
        if ctype == RES_STRING_POOL:
            strings = _string_pool(buf, off)
        elif ctype == RES_XML_START_ELEMENT:
            name = struct.unpack_from("<I", buf, off + 20)[0]
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", buf, off + 24)
            attrs = {}
            p = off + 16 + attr_start
            for i in range(attr_count):
                a = p + i * attr_size
                _, aname, raw = struct.unpack_from("<III", buf, a)
                dtype = buf[a + 15]
                data = struct.unpack_from("<I", buf, a + 16)[0]
                if raw != 0xFFFFFFFF:
                    val = strings[raw]
                elif dtype == TYPE_STRING:
                    val = strings[data]
                elif dtype == TYPE_BOOL:
                    val = data != 0
                elif dtype in (TYPE_INT_DEC, TYPE_INT_HEX):
                    val = data
                else:
                    val = "@0x%08x" % data
                attrs[strings[aname]] = val
            elements.append((depth, strings[name], attrs))
            depth += 1
        elif ctype == RES_XML_END_ELEMENT:
            depth -= 1
        off += csize
    return elements

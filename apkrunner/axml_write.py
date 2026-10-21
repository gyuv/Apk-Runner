"""Write minimal Android binary XML (enough for AndroidManifest.xml of sample apps)."""
import struct

from .axml import RES_STRING_POOL, RES_XML_END_ELEMENT, RES_XML_START_ELEMENT, TYPE_STRING


def _chunk(ctype, header_size, body):
    return struct.pack("<HHI", ctype, header_size, 8 + len(body)) + body


def encode(elements):
    """elements: list of (depth, tag, {attr: str}) in document order."""
    strings = []

    def sid(s):
        if s not in strings:
            strings.append(s)
        return strings.index(s)

    for _, tag, attrs in elements:
        sid(tag)
        for k, v in attrs.items():
            sid(k)
            sid(v)

    data, offsets = b"", []
    for s in strings:
        offsets.append(len(data))
        enc = s.encode("utf-16-le")
        data += struct.pack("<H", len(s)) + enc + b"\0\0"
    data += b"\0" * (-len(data) % 4)
    hdr = 28
    pool_body = struct.pack("<IIIII", len(strings), 0, 0, hdr + 4 * len(strings), 0)
    pool = _chunk(RES_STRING_POOL, hdr, pool_body + struct.pack("<%dI" % len(offsets), *offsets) + data)

    body, open_tags = b"", []

    def close_to(depth):
        nonlocal body
        while len(open_tags) > depth:
            name = open_tags.pop()
            body += _chunk(RES_XML_END_ELEMENT, 16, struct.pack("<IIII", 0, 0xFFFFFFFF, 0xFFFFFFFF, sid(name)))

    for depth, tag, attrs in elements:
        close_to(depth)
        ext = struct.pack("<IIHHHHHH", 0xFFFFFFFF, sid(tag), 20, 20, len(attrs), 0, 0, 0)
        for k, v in attrs.items():
            ext += struct.pack("<IIIHBBI", 0xFFFFFFFF, sid(k), sid(v), 8, 0, TYPE_STRING, sid(v))
        body += _chunk(RES_XML_START_ELEMENT, 16, struct.pack("<II", 0, 0xFFFFFFFF) + ext)
        open_tags.append(tag)
    close_to(0)
    return _chunk(0x0003, 8, pool + body)

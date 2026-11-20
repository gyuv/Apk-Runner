import io
import os
import struct
import tempfile
import unittest
import zipfile

from apkrunner import arsc, axml_write
from apkrunner.appmeta import meta
from apkrunner.macapp import want_landscape


def _chunk(ctype, hsize, header_rest, body):
    return struct.pack("<HHI", ctype, hsize, hsize + len(body)) + header_rest + body


def _pool(strings):
    data, offs = b"", []
    for s in strings:
        offs.append(len(data))
        b = s.encode("utf-8")
        data += bytes([len(s), len(b)]) + b + b"\0"
    data += b"\0" * (-len(data) % 4)
    hdr = struct.pack("<IIIII", len(strings), 0, 1 << 8, 28 + 4 * len(strings), 0)
    return _chunk(1, 28, hdr, struct.pack("<%dI" % len(offs), *offs) + data)


def _type(type_id, entries, lang=b"", density=0):
    """entries: list of (dtype, data) or None."""
    config = struct.pack("<I", 64) + b"\0" * 4 + lang.ljust(2, b"\0") + b"\0" * 4 + struct.pack("<H", density)
    config = config.ljust(64, b"\0")
    offsets, body = [], b""
    for e in entries:
        if e is None:
            offsets.append(0xFFFFFFFF)
            continue
        offsets.append(len(body))
        body += struct.pack("<HHI", 8, 0, 0) + struct.pack("<HBBI", 8, 0, e[0], e[1])
    idx = struct.pack("<%dI" % len(offsets), *offsets)
    hsize = 20 + len(config)
    start = hsize + len(idx)
    rest = struct.pack("<BBHII", type_id, 0, 0, len(entries), start) + config
    return _chunk(0x0201, hsize, rest, idx + body)


def build_arsc():
    g = _pool(["Fancy App", "Appli", "res/mipmap-mdpi/ic.png", "res/mipmap-xxxhdpi/ic.png"])
    types = (_type(1, [(3, 0)]) + _type(1, [(3, 1)], lang=b"fr")
             + _type(2, [(3, 2)], density=160) + _type(2, [(3, 3)], density=640)
             + _type(2, [None, (1, 0x7f020000)]))
    pkg_rest = struct.pack("<I", 0x7f) + b"\0" * 256 + struct.pack("<IIIII", 0, 0, 0, 0, 0)
    pkg = _chunk(0x0200, 8 + len(pkg_rest), pkg_rest, types)
    return _chunk(2, 12, struct.pack("<I", 1), g + pkg)


class AppMetaTest(unittest.TestCase):
    def test_table(self):
        t = arsc.Table(build_arsc())
        self.assertEqual(t.string(0x7f010000), "Fancy App")
        self.assertEqual(t.files(0x7f020000)[0], "res/mipmap-xxxhdpi/ic.png")
        self.assertEqual(t.files(0x7f020001)[0], "res/mipmap-xxxhdpi/ic.png")  # via reference

    def test_meta(self):
        manifest = axml_write.encode([
            (0, "manifest", {"package": "com.fancy"}),
            (1, "application", {"label": "@0x7f010000", "icon": "@0x7f020001"}),
            (2, "activity", {"name": ".Main", "screenOrientation": "0"}),
            (3, "intent-filter", {}),
            (4, "category", {"name": "android.intent.category.LAUNCHER"}),
        ])
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            z.writestr("AndroidManifest.xml", manifest)
            z.writestr("resources.arsc", build_arsc())
            z.writestr("res/mipmap-xxxhdpi/ic.png", b"\x89PNGbig")
            z.writestr("res/mipmap-mdpi/ic.png", b"\x89PNGsmall")
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "a.apk")
            with open(p, "wb") as f:
                f.write(buf.getvalue())
            m = meta(p)
        self.assertEqual((m["label"], m["icon_png"], m["orientation"]), ("Fancy App", b"\x89PNGbig", "landscape"))

    def test_sample_fallbacks(self):
        here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        m = meta(os.path.join(here, "samples", "hello", "hello.apk"))
        self.assertEqual(m["package"], "com.example.hello")
        self.assertTrue(m["label"])

    def test_orientation_priority(self):
        self.assertTrue(want_landscape("landscape", "portrait", "portrait"))
        self.assertFalse(want_landscape("auto", "landscape", "portrait"))
        self.assertTrue(want_landscape("auto", "landscape", None))
        self.assertTrue(want_landscape("auto", None, None, current=True))


if __name__ == "__main__":
    unittest.main()

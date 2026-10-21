import io
import os
import tempfile
import unittest
import zipfile

from apkrunner.bundle import _safe_join, merge


def _zip(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for n, d in entries.items():
            z.writestr(n, d)
    return buf.getvalue()


class BundleTest(unittest.TestCase):
    def test_merge_xapk(self):
        with tempfile.TemporaryDirectory() as t:
            x = os.path.join(t, "app.xapk")
            with open(x, "wb") as f:
                f.write(_zip({"base.apk": _zip({"AndroidManifest.xml": b""}),
                              "config.x86_64.apk": b"x", "manifest.json": b"{}"}))
            names = sorted(os.path.basename(p) for p in merge(x, os.path.join(t, "o")))
            self.assertEqual(names, ["base.apk", "config.x86_64.apk"])

    def test_zip_slip_blocked(self):
        with self.assertRaises(ValueError):
            _safe_join("/tmp/out", "../evil")


if __name__ == "__main__":
    unittest.main()

import os
import shutil
import subprocess
import sys
import tempfile
import unittest

from apkrunner import axml, axml_write
from apkrunner.inspect import inspect

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HELLO = os.path.join(ROOT, "samples", "hello", "hello.apk")
NOTES = os.path.join(ROOT, "samples", "notes", "notes.apk")


def run_app(apk, *steps):
    """Run headless on the java backend; return the --dump view tree."""
    with tempfile.TemporaryDirectory() as t:
        png = os.path.join(t, "shot.png")
        out = subprocess.run([sys.executable, "-m", "apkrunner", "run", apk, "--backend", "java",
                              "--screenshot", png, "--dump", *steps],
                             cwd=ROOT, check=True, timeout=300, capture_output=True, text=True).stdout
        with open(png, "rb") as f:
            assert f.read(8) == b"\x89PNG\r\n\x1a\n"
    return out


class AxmlWriteTest(unittest.TestCase):
    def test_roundtrip(self):
        els = [(0, "manifest", {"package": "a.b"}), (1, "application", {}), (2, "activity", {"name": ".X"})]
        self.assertEqual(axml.parse(axml_write.encode(els)), els)


class SampleTest(unittest.TestCase):
    def test_launcher_activity(self):
        info = inspect(HELLO)
        self.assertEqual(info["package"], "com.example.hello")
        self.assertEqual(info["main_activity"], "com.example.hello.MainActivity")

    @unittest.skipUnless(shutil.which("javac"), "needs a JDK")
    def test_hello_handles_taps(self):
        out = run_app(HELLO, "--tap", "200,163", "--tap", "200,163")
        self.assertIn('"Taps: 2"', out)

    @unittest.skipUnless(shutil.which("javac"), "needs a JDK")
    def test_notes_resources(self):
        out = run_app(NOTES)
        # strings, string-array, formatted string, include + RelativeLayout, inflated items
        for text in ('"My notes"', '"XML layouts and resources, no VM"', '"Buy milk"', '"2 notes"', '"Add"'):
            self.assertIn(text, out)
        self.assertIn("RelativeLayout", out)
        self.assertIn("ImageView [12,15 40x40]", out)  # 40dp logo from a 96px xhdpi PNG

    @unittest.skipUnless(shutil.which("javac"), "needs a JDK")
    def test_notes_typing_checkbox_onclick(self):
        out = run_app(NOTES, "--tap", "150,103", "--type", "Ship it", "--tap", "20,158", "--tap", "353,104")
        self.assertIn('"★ Ship it"', out)
        self.assertIn('"3 notes"', out)
        self.assertIn("checked", out)


if __name__ == "__main__":
    unittest.main()

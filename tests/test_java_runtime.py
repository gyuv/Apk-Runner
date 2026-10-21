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
    def test_runs_and_handles_taps(self):
        with tempfile.TemporaryDirectory() as t:
            png = os.path.join(t, "shot.png")
            subprocess.run([sys.executable, "-m", "apkrunner", "run", HELLO, "--backend", "java",
                            "--screenshot", png, "--tap", "200,163"], cwd=ROOT, check=True, timeout=300)
            with open(png, "rb") as f:
                self.assertEqual(f.read(8), b"\x89PNG\r\n\x1a\n")


if __name__ == "__main__":
    unittest.main()

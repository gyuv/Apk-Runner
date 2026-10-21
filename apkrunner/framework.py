"""Locate (or build) the Java implementation of the Android framework."""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(HERE), "framework", "src")
CACHE = os.path.join(os.path.expanduser("~"), ".apkrunner", "framework.jar")


def _bundled():
    base = getattr(sys, "_MEIPASS", HERE)  # PyInstaller unpack dir
    for p in (os.path.join(base, "apkrunner", "framework.jar"), os.path.join(HERE, "framework.jar")):
        if os.path.exists(p):
            return p
    return None


def build(out):
    """Compile framework/src into a jar (needs a JDK)."""
    javac = shutil.which("javac")
    if not javac:
        raise SystemExit("A JDK (javac) is needed to build the framework from source.")
    files = [os.path.join(d, f) for d, _, fs in os.walk(SRC) for f in fs if f.endswith(".java")]
    with tempfile.TemporaryDirectory() as t:
        subprocess.run([javac, "--release", "11", "-nowarn", "-d", t] + files, check=True)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
            for d, _, fs in os.walk(t):
                for f in fs:
                    p = os.path.join(d, f)
                    z.write(p, os.path.relpath(p, t))
    return out


def framework_jar():
    if os.path.isdir(SRC):  # source checkout: rebuild when sources are newer
        newest = max(os.path.getmtime(os.path.join(d, f)) for d, _, fs in os.walk(SRC) for f in fs)
        if not os.path.exists(CACHE) or os.path.getmtime(CACHE) < newest:
            build(CACHE)
        return CACHE
    jar = _bundled()
    if not jar:
        raise SystemExit("Android framework jar not found in this build.")
    return jar

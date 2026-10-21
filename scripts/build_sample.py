"""Build a sample APK from samples/<name>: compile against the framework, dex, pack.

Samples with res/ and AndroidManifest.xml are compiled with aapt2. Point --aapt2 and
--android-jar at an Android SDK (build-tools/*/aapt2, platforms/android-*/android.jar),
or set ANDROID_HOME and they are found automatically.
"""
import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from apkrunner import axml_write  # noqa: E402
from apkrunner.convert import dex2jar_home  # noqa: E402
from apkrunner.framework import framework_jar  # noqa: E402

HELLO_MANIFEST = [
    (0, "manifest", {"package": "com.example.hello", "versionName": "1.0"}),
    (1, "uses-sdk", {"minSdkVersion": "21", "targetSdkVersion": "34"}),
    (1, "application", {"label": "Hello"}),
    (2, "activity", {"name": ".MainActivity"}),
    (3, "intent-filter", {}),
    (4, "action", {"name": "android.intent.action.MAIN"}),
    (4, "category", {"name": "android.intent.category.LAUNCHER"}),
]


def sdk_tool(pattern):
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    hits = sorted(glob.glob(os.path.join(home, pattern))) if home else []
    return hits[-1] if hits else None


def build(name, aapt2=None, android_jar=None):
    sample = os.path.join(ROOT, "samples", name)
    out = os.path.join(sample, name + ".apk")
    with tempfile.TemporaryDirectory() as t:
        java_files = [os.path.join(d, f) for d, _, fs in os.walk(os.path.join(sample, "src")) for f in fs if f.endswith(".java")]
        base_apk = None
        if os.path.isdir(os.path.join(sample, "res")):
            aapt2 = aapt2 or sdk_tool("build-tools/*/aapt2")
            android_jar = android_jar or sdk_tool("platforms/android-*/android.jar")
            if not aapt2 or not android_jar:
                raise SystemExit("This sample has resources: pass --aapt2 and --android-jar (or set ANDROID_HOME).")
            flat = os.path.join(t, "res.zip")
            subprocess.run([aapt2, "compile", "--dir", os.path.join(sample, "res"), "-o", flat], check=True)
            gen = os.path.join(t, "gen")
            base_apk = os.path.join(t, "base.apk")
            subprocess.run([aapt2, "link", "-I", android_jar, "--manifest", os.path.join(sample, "AndroidManifest.xml"),
                            "--java", gen, "-o", base_apk, flat], check=True)
            java_files += [os.path.join(d, f) for d, _, fs in os.walk(gen) for f in fs if f.endswith(".java")]
        classes = os.path.join(t, "classes")
        subprocess.run(["javac", "--release", "8", "-nowarn", "-cp", framework_jar(), "-d", classes] + java_files, check=True)
        jar = os.path.join(t, "app.jar")
        shutil.make_archive(jar[:-4], "zip", classes)
        os.rename(jar[:-4] + ".zip", jar)
        dex = os.path.join(t, "classes.dex")
        lib = os.path.join(dex2jar_home(), "lib")
        cp = os.pathsep.join(os.path.join(lib, j) for j in os.listdir(lib) if j.endswith(".jar"))
        subprocess.run(["java", "-cp", cp, "com.googlecode.dex2jar.tools.Jar2Dex", "-f", "-o", dex, jar], check=True,
                       stdout=subprocess.DEVNULL)
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
            if base_apk:
                with zipfile.ZipFile(base_apk) as b:
                    for n in b.namelist():
                        z.writestr(b.getinfo(n), b.read(n))
            else:
                z.writestr("AndroidManifest.xml", axml_write.encode(HELLO_MANIFEST))
            z.write(dex, "classes.dex")
    print(out)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("name", nargs="?", default="hello", help="sample folder under samples/")
    p.add_argument("--aapt2")
    p.add_argument("--android-jar")
    a = p.parse_args()
    build(a.name, a.aapt2, a.android_jar)


if __name__ == "__main__":
    main()

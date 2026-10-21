"""Build samples/hello/hello.apk: compile against the framework, dex it, pack an APK."""
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

PKG, ACTIVITY = "com.example.hello", "com.example.hello.MainActivity"


def main():
    src = os.path.join(ROOT, "samples", "hello", "src")
    out = os.path.join(ROOT, "samples", "hello", "hello.apk")
    with tempfile.TemporaryDirectory() as t:
        classes = os.path.join(t, "classes")
        files = [os.path.join(d, f) for d, _, fs in os.walk(src) for f in fs if f.endswith(".java")]
        subprocess.run(["javac", "--release", "8", "-nowarn", "-cp", framework_jar(), "-d", classes] + files, check=True)
        jar = os.path.join(t, "app.jar")
        shutil.make_archive(jar[:-4], "zip", classes)
        os.rename(jar[:-4] + ".zip", jar)
        dex = os.path.join(t, "classes.dex")
        lib = os.path.join(dex2jar_home(), "lib")
        cp = os.pathsep.join(os.path.join(lib, j) for j in os.listdir(lib) if j.endswith(".jar"))
        subprocess.run(["java", "-cp", cp, "com.googlecode.dex2jar.tools.Jar2Dex", "-f", "-o", dex, jar], check=True)
        manifest = axml_write.encode([
            (0, "manifest", {"package": PKG, "versionName": "1.0"}),
            (1, "uses-sdk", {"minSdkVersion": "21", "targetSdkVersion": "34"}),
            (1, "application", {"label": "Hello"}),
            (2, "activity", {"name": ".MainActivity"}),
            (3, "intent-filter", {}),
            (4, "action", {"name": "android.intent.action.MAIN"}),
            (4, "category", {"name": "android.intent.category.LAUNCHER"}),
        ])
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("AndroidManifest.xml", manifest)
            z.write(dex, "classes.dex")
    print(out)


if __name__ == "__main__":
    main()

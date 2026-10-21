"""DEX -> JVM .jar conversion via dex2jar (downloaded once, cached)."""
import io
import os
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

from .bundle import split_apks

DEX2JAR_URL = "https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip"
CACHE = os.path.join(os.path.expanduser("~"), ".apkrunner", "tools")


def dex2jar_home():
    home = os.path.join(CACHE, "dex-tools-v2.4")
    if not os.path.isdir(os.path.join(home, "lib")):
        os.makedirs(CACHE, exist_ok=True)
        print("Downloading dex2jar (one time)...")
        with urllib.request.urlopen(DEX2JAR_URL) as r:
            zipfile.ZipFile(io.BytesIO(r.read())).extractall(CACHE)
    return home


def convert(apk, out_jar):
    """Convert every DEX of an APK/bundle into one JVM .jar."""
    java = shutil.which("java")
    if not java:
        raise SystemExit("Java 8+ is required for conversion (install OpenJDK).")
    cp = os.pathsep.join(os.path.join(dex2jar_home(), "lib", j)
                         for j in os.listdir(os.path.join(dex2jar_home(), "lib")) if j.endswith(".jar"))
    with tempfile.TemporaryDirectory() as t:
        jars = []
        for name, data in split_apks(apk).items():
            src = os.path.join(t, name)
            with open(src, "wb") as f:
                f.write(data)
            if not any(n.endswith(".dex") for n in zipfile.ZipFile(src).namelist()):
                continue
            jar = src + ".jar"
            subprocess.run([java, "-cp", cp, "com.googlecode.dex2jar.tools.Dex2jarCmd",
                            "-f", "-o", jar, src], check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            jars.append(jar)
        _merge_jars(jars, out_jar)
    return out_jar


def _merge_jars(jars, out_jar):
    seen = set()
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as out:
        for j in jars:
            with zipfile.ZipFile(j) as z:
                for n in z.namelist():
                    if n not in seen:
                        seen.add(n)
                        out.writestr(n, z.read(n))

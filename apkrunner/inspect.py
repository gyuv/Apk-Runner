"""Inspect an APK / XAPK / APKM and decide how it can run on this host."""
import io
import platform
import zipfile

from . import axml
from .bundle import split_apks

# Host CPU -> Android ABIs it executes natively (best first).
NATIVE_ABIS = {
    "arm64": ["arm64-v8a"],
    "x86_64": ["x86_64", "x86"],
    "x86": ["x86"],
}


def host_arch():
    m = platform.machine().lower()
    if m in ("arm64", "aarch64"):
        return "arm64"
    if m in ("x86_64", "amd64"):
        return "x86_64"
    if m in ("i386", "i686", "x86"):
        return "x86"
    return m


def _open_apks(path):
    """Yield (name, ZipFile) for the APK, or every APK inside a bundle."""
    for name, data in split_apks(path).items():
        yield name, zipfile.ZipFile(io.BytesIO(data))


def inspect(path, arch=None):
    arch = arch or host_arch()
    info = {"path": path, "host": arch, "splits": [], "abis": set(),
            "dex": 0, "permissions": [], "package": None}
    for name, z in _open_apks(path):
        names = z.namelist()
        info["splits"].append(name)
        info["dex"] += sum(1 for n in names if n.endswith(".dex"))
        for n in names:
            if n.startswith("lib/") and n.endswith(".so"):
                info["abis"].add(n.split("/")[1])
        for depth, tag, attrs in axml.parse(z.read("AndroidManifest.xml")):
            if tag == "manifest" and not attrs.get("split"):
                info["package"] = attrs.get("package")
                info["version"] = attrs.get("versionName")
            elif tag == "uses-sdk":
                info["min_sdk"] = attrs.get("minSdkVersion")
                info["target_sdk"] = attrs.get("targetSdkVersion")
            elif tag == "uses-permission":
                info["permissions"].append(attrs.get("name"))
            elif tag == "activity" and depth == 2 and "main_activity" not in info:
                info["main_activity"] = attrs.get("name")
    info["abis"] = sorted(info["abis"])
    info["verdict"], info["abi"] = verdict(info["abis"], arch)
    return info


def verdict(abis, arch):
    if not abis:
        return "native", None  # pure Java/Kotlin: runs anywhere with the runtime
    for abi in NATIVE_ABIS.get(arch, []):
        if abi in abis:
            return "native", abi
    if arch.startswith("x86") and ("arm64-v8a" in abis or "armeabi-v7a" in abis):
        return "needs-translation", "arm64-v8a" if "arm64-v8a" in abis else "armeabi-v7a"
    return "unsupported", None

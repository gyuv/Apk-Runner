"""One-click Android runtime setup: no Android Studio, Homebrew, Java or sdkmanager.

Downloads Google's official packages (platform-tools, emulator, one system image) straight
from dl.google.com into the standard SDK folder, checks their SHA-1, and writes a small
virtual device ("AVD") for Apk-Runner.
"""
import hashlib
import os
import platform
import shutil
import stat
import tempfile
import threading
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = "https://dl.google.com/android/repository/"
SYSIMG_LIST = REPO + "sys-img/google_apis/sys-img2-3.xml"
REPO_LIST = REPO + "repository2-3.xml"
API = 30  # Android 11: its x86_64 image can also run ARM-only apps (built-in translation)
AVD = "apkrunner"

# Progress shared with the dashboard: {"state": idle|running|done|error, "step", "done", "total", "error"}
PROGRESS = {"state": "idle"}
_LOCK = threading.Lock()


def sdk_root():
    env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env:
        return env
    home = os.path.expanduser("~")
    if platform.system() == "Darwin":
        return os.path.join(home, "Library", "Android", "sdk")
    if platform.system() == "Windows":
        return os.path.join(os.environ.get("LOCALAPPDATA", home), "Android", "Sdk")
    return os.path.join(home, "Android", "Sdk")


def host():
    """(host-os, host-arch, image abi) as used in Google's package lists."""
    system = {"Darwin": "macosx", "Windows": "windows", "Linux": "linux"}.get(platform.system(), "linux")
    arm = platform.machine().lower() in ("arm64", "aarch64")
    return system, ("aarch64" if arm else "x64"), ("arm64-v8a" if arm else "x86_64")


def _local(tag):
    return tag.rsplit("}", 1)[-1]


def _child(el, name):
    for c in el:
        if _local(c.tag) == name:
            return c
    return None


def _text(el, *path):
    for name in path:
        el = _child(el, name) if el is not None else None
    return (el.text or "").strip() if el is not None else None


def _rev(pkg):
    r = _child(pkg, "revision")
    return tuple(int(_text(r, k) or 0) for k in ("major", "minor", "micro")) if r is not None else ()


def pick(xml_bytes, path, host_os, host_arch, base):
    """Best stable archive for `path`: {"url", "size", "sha1", "revision"} or None."""
    root = ET.fromstring(xml_bytes)
    stable = {c.get("id") for c in root.iter() if _local(c.tag) == "channel" and (c.text or "").strip() == "stable"}
    best = None
    for pkg in root.iter():
        if _local(pkg.tag) != "remotePackage" or pkg.get("path") != path:
            continue
        ch = _child(pkg, "channelRef")
        if ch is not None and stable and ch.get("ref") not in stable:
            continue
        archives = _child(pkg, "archives")
        for a in (archives if archives is not None else []):
            os_, arch = _text(a, "host-os"), _text(a, "host-arch")
            if os_ and os_ != host_os:
                continue
            if arch and arch != host_arch:
                continue
            comp = _child(a, "complete")
            cand = {"url": base + _text(comp, "url"), "size": int(_text(comp, "size") or 0),
                    "sha1": _text(comp, "checksum"), "revision": _rev(pkg)}
            if best is None or cand["revision"] > best["revision"] or (
                    cand["revision"] == best["revision"] and arch):  # prefer arch-specific build
                best = cand
    return best


def plan(host_info=None, api=API):
    """Resolve the three packages to download for this machine."""
    host_os, host_arch, abi = host_info or host()
    repo = urllib.request.urlopen(REPO_LIST, timeout=30).read()
    imgs = urllib.request.urlopen(SYSIMG_LIST, timeout=30).read()
    img_path = "system-images;android-%d;google_apis;%s" % (api, abi)
    parts = [
        ("platform-tools", pick(repo, "platform-tools", host_os, host_arch, REPO), ""),
        ("emulator", pick(repo, "emulator", host_os, host_arch, REPO), ""),
        (img_path, pick(imgs, img_path, host_os, host_arch, REPO + "sys-img/google_apis/"),
         os.path.join("system-images", "android-%d" % api, "google_apis")),
    ]
    missing = [name for name, p, _ in parts if not p]
    if missing:
        raise RuntimeError("Google's package list has no %s for %s/%s" % (", ".join(missing), host_os, host_arch))
    return parts, abi


def _download(url, size, sha1, dest, label):
    h = hashlib.sha1()
    PROGRESS.update(step=label, done=0, total=size)
    with urllib.request.urlopen(url, timeout=60) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            h.update(chunk)
            PROGRESS["done"] += len(chunk)
    if sha1 and h.hexdigest() != sha1:
        raise RuntimeError("download of %s was corrupted (checksum mismatch); try again" % label)


def extract(zpath, dest):
    """Unzip keeping executable bits and symlinks (zipfile drops both by default)."""
    root = os.path.realpath(dest)
    pending = []
    with zipfile.ZipFile(zpath) as z:
        for info in z.infolist():
            target = os.path.realpath(os.path.join(dest, info.filename))
            if target != root and not target.startswith(root + os.sep):
                raise RuntimeError("unsafe path in archive: %s" % info.filename)
            mode = info.external_attr >> 16
            if info.is_dir():
                os.makedirs(target, exist_ok=True)
                continue
            os.makedirs(os.path.dirname(target), exist_ok=True)
            if stat.S_ISLNK(mode):
                if os.path.lexists(target):
                    os.remove(target)
                link = z.read(info).decode()
                try:
                    os.symlink(link, target)
                except OSError:  # e.g. Windows without symlink rights: copy the file instead
                    pending.append((target, os.path.join(os.path.dirname(target), link)))
                continue
            with z.open(info) as src, open(target, "wb") as out:
                shutil.copyfileobj(src, out)
            if mode & 0o111:
                os.chmod(target, (mode & 0o777) | 0o600)
    for target, src in pending:
        if os.path.isfile(src):
            shutil.copy2(src, target)


def write_avd(sdk, abi, api=API, name=AVD):
    avd_home = os.environ.get("ANDROID_AVD_HOME") or os.path.join(os.path.expanduser("~"), ".android", "avd")
    avd_dir = os.path.join(avd_home, name + ".avd")
    os.makedirs(avd_dir, exist_ok=True)
    sysdir = "system-images/android-%d/google_apis/%s/" % (api, abi)
    config = {
        "AvdId": name, "avd.ini.displayname": "Apk-Runner", "avd.ini.encoding": "UTF-8",
        "abi.type": abi, "hw.cpu.arch": "arm64" if abi.startswith("arm64") else "x86_64",
        "image.sysdir.1": sysdir, "tag.id": "google_apis", "tag.display": "Google APIs",
        "target": "android-%d" % api, "PlayStore.enabled": "false",
        "hw.lcd.width": "720", "hw.lcd.height": "1280", "hw.lcd.density": "320",
        "hw.ramSize": "2048", "vm.heapSize": "256", "disk.dataPartition.size": "6G",
        "hw.keyboard": "yes", "hw.gpu.enabled": "yes", "hw.gpu.mode": "auto",
        "hw.audioInput": "no", "showDeviceFrame": "no", "hw.cpu.ncore": "2",
    }
    with open(os.path.join(avd_dir, "config.ini"), "w") as f:
        f.writelines("%s=%s\n" % kv for kv in config.items())
    with open(os.path.join(avd_home, name + ".ini"), "w") as f:
        f.write("avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/%s.avd\ntarget=android-%d\n" % (avd_dir, name, api))
    return avd_dir


def installed(sdk=None):
    sdk = sdk or sdk_root()
    host_os, _, abi = host()
    exe = ".exe" if host_os == "windows" else ""
    return all(os.path.exists(p) for p in (
        os.path.join(sdk, "platform-tools", "adb" + exe),
        os.path.join(sdk, "emulator", "emulator" + exe),
        os.path.join(sdk, "system-images", "android-%d" % API, "google_apis", abi, "system.img"),
    ))


class _ProcessLock:
    """Only one Apk-Runner process downloads at a time; others wait, then reuse the result."""

    def __enter__(self):
        path = os.path.join(os.path.expanduser("~"), ".apkrunner", "setup.lock")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.f = open(path, "w")
        try:
            import fcntl
            fcntl.flock(self.f, fcntl.LOCK_EX)
        except ImportError:  # Windows: no cross-process lock, the per-process one still applies
            pass
        return self

    def __exit__(self, *exc):
        self.f.close()


def setup(log=print):
    """Download and install everything that's missing. Safe to run again."""
    with _LOCK:
        if PROGRESS.get("state") == "running":
            return
        PROGRESS.clear()
        PROGRESS.update(state="running", step="Checking Google's package list", done=0, total=0)
    try:
        PROGRESS.update(step="Waiting for another Apk-Runner window to finish setting up Android")
        with _ProcessLock():
            PROGRESS.update(step="Checking Google's package list")
            _setup(log)
    except Exception as e:
        PROGRESS.update(state="error", error=str(e))
        raise


def _setup(log):
    if True:
        sdk = sdk_root()
        os.makedirs(sdk, exist_ok=True)
        parts, abi = plan()
        with tempfile.TemporaryDirectory() as tmp:
            for name, p, sub in parts:
                dest = os.path.join(sdk, sub)
                marker = os.path.join(sdk, sub, name.split(";")[-1] if sub else name, "source.properties")
                if os.path.exists(marker):
                    log("%s: already installed" % name)
                    continue
                label = name.split(";")[0] if ";" not in name else "Android %d system image" % API
                log("Downloading %s (%d MB)..." % (label, p["size"] >> 20))
                zpath = os.path.join(tmp, "pkg.zip")
                _download(p["url"], p["size"], p["sha1"], zpath, label)
                PROGRESS.update(step="Unpacking " + label)
                os.makedirs(dest, exist_ok=True)
                extract(zpath, dest)
                os.remove(zpath)
        write_avd(sdk, abi)
        os.environ.setdefault("ANDROID_HOME", sdk)
        PROGRESS.update(state="done", step="Android is ready")
        log("Android is ready (%s)" % sdk)


def start_in_background():
    t = threading.Thread(target=lambda: _quiet(setup), daemon=True)
    t.start()
    return t


def _quiet(fn):
    try:
        fn(log=lambda *_: None)
    except Exception:
        pass  # recorded in PROGRESS for the dashboard

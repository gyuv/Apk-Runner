"""ApkStart-style app management for macOS.

Every installed Android app gets its own real Mac app in ~/Applications/Android Apps/
(name and icon taken from the APK), so it shows up in Launchpad, Spotlight and the Dock
like a native app. Opening it starts the app in its own window; the Android runtime is
started on demand and stopped again when the last app window closes.
"""
import json
import os
import platform
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile

from . import backends
from .appmeta import meta
from .bundle import merge

HOME = os.path.expanduser("~")
STATE = os.path.join(HOME, ".apkrunner")
APKS = os.path.join(STATE, "apks")
ICONS = os.path.join(STATE, "icons")
SESSIONS = os.path.join(STATE, "sessions")
REGISTRY = os.path.join(STATE, "apps.json")
CONFIG = os.path.join(STATE, "config.json")
APPS_DIR = os.path.join(HOME, "Applications", "Android Apps")
SHARED = os.path.join(HOME, "ApkRunnerShared")
DEVICE_SHARED = "/sdcard/ApkRunnerShared"
DEFAULTS = {"idle_shutdown": True, "shared_folder": True, "orientation": "auto"}
IS_MAC = platform.system() == "Darwin"


# ---- small JSON stores -------------------------------------------------------------
def _load(path, default):
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def _save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    os.replace(tmp, path)


def registry():
    return _load(REGISTRY, {})


def config():
    return dict(DEFAULTS, **_load(CONFIG, {}))


def set_config(key, value):
    if key not in DEFAULTS:
        raise SystemExit("unknown setting %r (known: %s)" % (key, ", ".join(DEFAULTS)))
    if isinstance(DEFAULTS[key], bool):
        value = str(value).lower() in ("1", "true", "yes", "on")
    cur = _load(CONFIG, {})
    cur[key] = value
    _save(CONFIG, cur)


# ---- orientation (same priority order as ApkStart) ---------------------------------
def want_landscape(mode, natural, override, current=False):
    """settings force > per-app override > manifest orientation > keep current."""
    for v in (mode if mode != "auto" else None, override, natural):
        if v == "landscape":
            return True
        if v == "portrait":
            return False
    return current


# ---- .app bundle ---------------------------------------------------------------
def self_command():
    """argv that re-runs this tool (frozen binary, or python -m apkrunner)."""
    if getattr(sys, "frozen", False):
        return [sys.executable]
    pkg_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return ["/usr/bin/env", "PYTHONPATH=" + pkg_root, sys.executable, "-m", "apkrunner"]


def _safe_name(label):
    name = re.sub(r'[/:\\\0]', "-", label).strip(". ") or "Android App"
    return name[:60]


def _sh_quote(s):
    return "'" + s.replace("'", "'\\''") + "'"


def _icns(png, dest):
    """PNG/WebP bytes -> .icns with the built-in sips tool; False if not possible."""
    if not png or not shutil.which("sips"):
        return False
    with tempfile.TemporaryDirectory() as t:
        src, flat = os.path.join(t, "icon"), os.path.join(t, "icon.png")
        with open(src, "wb") as f:
            f.write(png)
        quiet = {"stdout": subprocess.DEVNULL, "stderr": subprocess.DEVNULL}
        ok = subprocess.call(["sips", "-s", "format", "png", "-Z", "512", src, "--out", flat], **quiet) == 0
        return ok and subprocess.call(["sips", "-s", "format", "icns", flat, "--out", dest], **quiet) == 0


def build_bundle(app_dir, label, bundle_id, argv, icon_png=None, extra_plist=None):
    """Write a minimal .app whose executable is a shell script running argv."""
    shutil.rmtree(app_dir, ignore_errors=True)
    macos = os.path.join(app_dir, "Contents", "MacOS")
    res = os.path.join(app_dir, "Contents", "Resources")
    os.makedirs(macos)
    os.makedirs(res)
    exe = os.path.join(macos, "launch")
    with open(exe, "w") as f:
        f.write("#!/bin/sh\nexec %s \"$@\"\n" % " ".join(_sh_quote(a) for a in argv))
    os.chmod(exe, 0o755)
    plist = {
        "CFBundleName": label, "CFBundleDisplayName": label,
        "CFBundleIdentifier": bundle_id, "CFBundleExecutable": "launch",
        "CFBundlePackageType": "APPL", "CFBundleVersion": "1",
        "LSMinimumSystemVersion": "10.13", "NSHighResolutionCapable": True,
    }
    if _icns(icon_png, os.path.join(res, "AppIcon.icns")):
        plist["CFBundleIconFile"] = "AppIcon"
    plist.update(extra_plist or {})
    with open(os.path.join(app_dir, "Contents", "Info.plist"), "wb") as f:
        plistlib.dump(plist, f)
    _register(app_dir)
    return app_dir


def _register(app_dir):
    lsreg = ("/System/Library/Frameworks/CoreServices.framework/Frameworks/"
             "LaunchServices.framework/Support/lsregister")
    if os.path.exists(lsreg):
        subprocess.call([lsreg, "-f", app_dir], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


# ---- install / remove / list ----------------------------------------------------------
def install(apk, launch_after=False, opts=None):
    m = meta(apk)
    pkg = m["package"]
    os.makedirs(APKS, exist_ok=True)
    ext = os.path.splitext(apk)[1].lower() or ".apk"
    stored = os.path.join(APKS, pkg + ext)
    if os.path.abspath(apk) != os.path.abspath(stored):
        shutil.copyfile(apk, stored)
    reg = registry()
    old = reg.get(pkg, {})
    app_path = None
    if IS_MAC:
        if old.get("app") and os.path.isdir(old["app"]):
            shutil.rmtree(old["app"], ignore_errors=True)
        app_path = build_bundle(os.path.join(APPS_DIR, _safe_name(m["label"]) + ".app"), m["label"],
                                "io.github.apkrunner.app." + re.sub(r"[^A-Za-z0-9.]", "-", pkg),
                                self_command() + ["--no-update-check", "launch", pkg], m["icon_png"])
    if m["icon_png"]:
        os.makedirs(ICONS, exist_ok=True)
        with open(os.path.join(ICONS, pkg + ".img"), "wb") as f:
            f.write(m["icon_png"])
    reg[pkg] = {"label": m["label"], "apk": stored, "orientation": m["orientation"],
                "override": old.get("override"), "app": app_path, "installed_on_device": False}
    _save(REGISTRY, reg)
    print("Installed %s (%s)" % (m["label"], pkg))
    if app_path:
        print("  app: %s  (Launchpad / Spotlight: \"%s\")" % (app_path, m["label"]))
    if launch_after:
        return launch(pkg, opts)
    return 0


def uninstall(pkg):
    reg = registry()
    pkg = _resolve(pkg, reg)
    entry = reg.pop(pkg)
    if entry.get("app"):
        shutil.rmtree(entry["app"], ignore_errors=True)
    for f in (entry["apk"], os.path.join(ICONS, pkg + ".img")):
        try:
            os.remove(f)
        except OSError:
            pass
    emu = backends.Emulator()
    if emu.available() and emu._device_ready():
        subprocess.call([emu.adb(), "uninstall", pkg], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    _save(REGISTRY, reg)
    print("Removed %s" % entry["label"])
    return 0


def _resolve(name, reg):
    if name in reg:
        return name
    hits = [p for p, e in reg.items() if e["label"].lower() == name.lower()]
    if len(hits) == 1:
        return hits[0]
    raise SystemExit("no installed app %r (see `apkrunner list`)" % name)


def list_apps():
    reg = registry()
    if not reg:
        print("No apps installed. Install one with: apkrunner install app.apk")
    for pkg, e in sorted(reg.items(), key=lambda x: x[1]["label"].lower()):
        o = e.get("override") or e.get("orientation") or "auto"
        print("%-28s %-40s %s" % (e["label"][:28], pkg, o))
    return 0


def rotate(pkg, side):
    reg = registry()
    pkg = _resolve(pkg, reg)
    reg[pkg]["override"] = None if side == "auto" else side
    _save(REGISTRY, reg)
    print("%s: orientation %s" % (reg[pkg]["label"], side))
    return 0


# ---- launch ------------------------------------------------------------------------
def _session_begin():
    os.makedirs(SESSIONS, exist_ok=True)
    path = os.path.join(SESSIONS, str(os.getpid()))
    open(path, "w").close()
    return path


def _other_sessions():
    alive = 0
    for name in os.listdir(SESSIONS) if os.path.isdir(SESSIONS) else []:
        pid = int(name) if name.isdigit() else 0
        if pid == os.getpid():
            continue
        try:
            os.kill(pid, 0)
            alive += 1
        except (OSError, ValueError):
            try:
                os.remove(os.path.join(SESSIONS, name))
            except OSError:
                pass
    return alive


def _adb(emu, *args, **kw):
    kw.setdefault("stdout", subprocess.DEVNULL)
    kw.setdefault("stderr", subprocess.DEVNULL)
    return subprocess.call([emu.adb()] + list(args), **kw)


def _shell(emu, cmd):
    return subprocess.run([emu.adb(), "shell", cmd], capture_output=True, text=True).stdout.strip()


def _installed(emu, pkg):
    return ("package:" + pkg) in _shell(emu, "pm list packages " + pkg).split()


def _launcher_activity(emu, pkg):
    out = _shell(emu, "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER " + pkg)
    lines = [l for l in out.splitlines() if "/" in l]
    return lines[-1].strip() if lines else None


def launch(name, opts=None):
    reg = registry()
    pkg = _resolve(name, reg)
    entry, cfg = reg[pkg], config()
    apk = entry["apk"]

    native = backends.MacApk()
    if native.available() and getattr(opts, "backend", None) in (None, "macapk"):
        return native.run(apk, {"package": pkg}, opts)

    emu = backends.Emulator()
    if not emu.available():
        _notify("Apk-Runner", "No Android runtime found. Run `apkrunner doctor`.")
        return 1
    session = _session_begin()
    booted_here = not emu._device_ready()
    try:
        if booted_here:
            _notify(entry["label"], "Starting Android runtime…")
        emu.boot(getattr(opts, "avd", None), lite=not getattr(opts, "full", False))
        if not _installed(emu, pkg):
            files = merge(apk, os.path.join(STATE, "splits", pkg))
            cmd = ["install-multiple" if len(files) > 1 else "install", "-r", "-g"] + files
            if _adb(emu, *cmd, stdout=None, stderr=None) != 0:
                return 1
        land = want_landscape(cfg["orientation"], entry.get("orientation"), entry.get("override"))
        _shell(emu, "settings put system accelerometer_rotation 0; "
                    "settings put system user_rotation %d" % (1 if land else 0))
        if cfg["shared_folder"]:
            os.makedirs(SHARED, exist_ok=True)
            _shell(emu, "mkdir -p " + DEVICE_SHARED)
            _adb(emu, "push", SHARED + "/.", DEVICE_SHARED)
        act = _launcher_activity(emu, pkg)
        if act:
            _shell(emu, "am start -n " + act)
        else:
            _shell(emu, "monkey -p %s -c android.intent.category.LAUNCHER 1" % pkg)
        scrcpy = shutil.which("scrcpy")
        if not scrcpy or getattr(opts, "no_window", False):
            if not scrcpy:
                print("scrcpy not found: install it (brew install scrcpy) to get an app window")
            return 0
        # The window lives as long as the app; closing it ends the session.
        subprocess.call([scrcpy, "--window-title", entry["label"], "--stay-awake"])
        if cfg["shared_folder"]:
            _adb(emu, "pull", DEVICE_SHARED + "/.", SHARED)
        return 0
    finally:
        try:
            os.remove(session)
        except OSError:
            pass
        if cfg["idle_shutdown"] and booted_here and _other_sessions() == 0 \
                and not getattr(opts, "no_window", False):
            _adb(emu, "emu", "kill")


def _notify(title, text):
    if IS_MAC and shutil.which("osascript"):
        script = 'display notification %s with title %s' % (json.dumps(text), json.dumps(title))
        subprocess.Popen(["osascript", "-e", script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        print("%s: %s" % (title, text))


# ---- .apk file association (double-click an APK in Finder) ---------------------------
def associate():
    if not IS_MAC:
        raise SystemExit("`associate` is macOS only")
    if not shutil.which("osacompile"):
        raise SystemExit("osacompile not found")
    app = os.path.join(APPS_DIR, "APK Installer.app")
    cmd = " ".join(_sh_quote(a) for a in self_command() + ["--no-update-check", "install", "--launch"])
    script = ('on open theFiles\n repeat with f in theFiles\n'
              '  do shell script %s & " " & quoted form of POSIX path of f & " > /dev/null 2>&1 &"\n'
              ' end repeat\nend open\n'
              'on run\n display dialog "Drop .apk, .xapk or .apkm files on APK Installer, '
              'or double-click one in Finder." buttons {"OK"} default button 1\nend run\n') % json.dumps(cmd)
    shutil.rmtree(app, ignore_errors=True)
    os.makedirs(APPS_DIR, exist_ok=True)
    subprocess.check_call(["osacompile", "-o", app, "-e", script])
    plist_path = os.path.join(app, "Contents", "Info.plist")
    with open(plist_path, "rb") as f:
        plist = plistlib.load(f)
    plist["CFBundleIdentifier"] = "io.github.apkrunner.installer"
    plist["CFBundleDocumentTypes"] = [{
        "CFBundleTypeName": "Android Package", "CFBundleTypeRole": "Viewer", "LSHandlerRank": "Owner",
        "CFBundleTypeExtensions": ["apk", "xapk", "apkm", "apks"],
        "LSItemContentTypes": ["com.android.package-archive", "public.data"],
    }]
    with open(plist_path, "wb") as f:
        plistlib.dump(plist, f)
    _register(app)
    duti = shutil.which("duti")
    for ext in ("apk", "xapk", "apkm"):
        if duti:
            subprocess.call([duti, "-s", "io.github.apkrunner.installer", "." + ext, "all"])
    print("Created %s" % app)
    if not duti:
        print("To make it the default: right-click any .apk > Get Info > Open with: APK Installer > Change All…")
    return 0

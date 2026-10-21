"""Check GitHub for new releases and update the standalone binary in place."""
import json
import os
import platform
import sys
import tempfile
import time
import urllib.request

from . import __version__

REPO = "gyuv/Apk-Runner"
API = "https://api.github.com/repos/%s/releases/latest" % REPO
STATE = os.path.join(os.path.expanduser("~"), ".apkrunner", "update-check.json")
CHECK_EVERY = 24 * 3600


def _ver(tag):
    try:
        return tuple(int(x) for x in tag.lstrip("v").split("."))
    except ValueError:
        return ()


def asset_name():
    """Release asset for this machine (raw binaries, not the dmg)."""
    system, machine = platform.system(), platform.machine().lower()
    if system == "Windows":
        return "apkrunner-windows-x64.exe"
    if system == "Darwin":
        return "apkrunner-macos-apple-silicon" if machine == "arm64" else "apkrunner-macos-intel"
    if system == "Linux" and machine in ("x86_64", "amd64"):
        return "apkrunner-linux-x64"
    return None


def latest(timeout=3):
    req = urllib.request.Request(API, headers={"Accept": "application/vnd.github+json",
                                               "User-Agent": "apkrunner/" + __version__})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)


def _load_state():
    try:
        with open(STATE) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def check_on_start():
    """At most once a day, print a notice when a newer release exists. Never raises."""
    if os.environ.get("APKRUNNER_NO_UPDATE_CHECK"):
        return
    try:
        state = _load_state()
        if time.time() - state.get("checked", 0) >= CHECK_EVERY:
            state = {"checked": time.time(), "tag": latest(timeout=2).get("tag_name", "")}
            os.makedirs(os.path.dirname(STATE), exist_ok=True)
            with open(STATE, "w") as f:
                json.dump(state, f)
        tag = state.get("tag", "")
        if _ver(tag) > _ver(__version__):
            print("Apk-Runner %s is available (you have %s). Run `apkrunner update` to install it."
                  % (tag, __version__), file=sys.stderr)
    except Exception:
        pass  # offline, rate-limited, etc.: never get in the way


def update(force=False):
    rel = latest(timeout=15)
    tag = rel.get("tag_name", "")
    if _ver(tag) <= _ver(__version__) and not force:
        print("You have the latest version (%s)." % __version__)
        return 0
    if not getattr(sys, "frozen", False):
        print("Apk-Runner %s is available. This copy runs from Python source, so update it with:\n"
              "  git pull   (or download a build from https://github.com/%s/releases/latest)" % (tag, REPO))
        return 0
    name = asset_name()
    url = next((a["browser_download_url"] for a in rel.get("assets", []) if a["name"] == name), None)
    if not url:
        print("No %s build in release %s; download it from %s" % (name, tag, rel.get("html_url")))
        return 1
    exe = os.path.realpath(sys.executable)
    print("Downloading %s %s..." % (name, tag))
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(exe), prefix=".apkrunner-update-")
    with os.fdopen(fd, "wb") as f, urllib.request.urlopen(url, timeout=60) as r:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    os.chmod(tmp, 0o755)
    if platform.system() == "Windows":
        old = exe + ".old"  # a running exe can be renamed but not overwritten
        if os.path.exists(old):
            os.remove(old)
        os.rename(exe, old)
        os.rename(tmp, exe)
    else:
        os.replace(tmp, exe)
    print("Updated to %s. The new version is used from the next command." % tag)
    return 0


def cleanup_old():
    """Remove the previous exe left behind by a Windows update."""
    if getattr(sys, "frozen", False):
        try:
            os.remove(os.path.realpath(sys.executable) + ".old")
        except OSError:
            pass

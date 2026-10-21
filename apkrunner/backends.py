"""Launch backends, chosen per host."""
import glob
import os
import platform
import shutil
import subprocess
import time

from .bundle import merge


def _which(*names):
    for n in names:
        p = shutil.which(n)
        if p:
            return p
    return None


def _sdk_tool(rel):
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(env)
        if root:
            for ext in ("", ".exe", ".bat"):
                p = os.path.join(root, rel + ext)
                if os.path.exists(p):
                    return p
    return None


class MacApk:
    """kksimp/Mac.apk: native, VM-free (Apple Silicon only)."""
    name = "macapk"

    @staticmethod
    def app():
        hits = glob.glob("/Applications/Mac.apk.app") + glob.glob(os.path.expanduser("~/Applications/Mac.apk.app"))
        return hits[0] if hits else None

    def available(self):
        return platform.system() == "Darwin" and platform.machine() == "arm64" and self.app() is not None

    def run(self, apk, info, opts):
        return subprocess.call(["open", "-a", self.app(), apk])


class Emulator:
    """Android emulator + adb: works on Windows, Intel/ARM macOS, Linux (uses a VM)."""
    name = "emulator"

    def adb(self):
        return _sdk_tool("platform-tools/adb") or _which("adb")

    def emulator(self):
        return _sdk_tool("emulator/emulator") or _which("emulator")

    def available(self):
        return self.adb() is not None

    def _device_ready(self):
        out = subprocess.run([self.adb(), "devices"], capture_output=True, text=True).stdout
        return any(l.endswith("\tdevice") for l in out.splitlines()[1:])

    def boot(self, avd, lite=True):
        if self._device_ready():
            return
        emu = self.emulator()
        if not emu:
            raise SystemExit("No running device and no emulator found. Install the Android SDK emulator (see README).")
        if not avd:
            avds = subprocess.run([emu, "-list-avds"], capture_output=True, text=True).stdout.split()
            if not avds:
                raise SystemExit("No AVD found. Create one: see README 'Intel Mac / Windows setup'.")
            avd = avds[0]
        args = [emu, "-avd", avd, "-no-snapshot-save", "-no-boot-anim"]
        if lite:
            args += ["-memory", "2048", "-no-audio", "-gpu", "auto"]
        subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.call([self.adb(), "wait-for-device"])
        while subprocess.run([self.adb(), "shell", "getprop", "sys.boot_completed"],
                             capture_output=True, text=True).stdout.strip() != "1":
            time.sleep(2)

    def run(self, apk, info, opts):
        self.boot(opts.avd, lite=not opts.full)
        work = os.path.join(os.path.expanduser("~"), ".apkrunner", "splits")
        shutil.rmtree(work, ignore_errors=True)
        files = merge(apk, work)
        cmd = [self.adb(), "install-multiple" if len(files) > 1 else "install", "-r", "-g"] + files
        if subprocess.call(cmd) != 0:
            return 1
        pkg = info["package"]
        subprocess.call([self.adb(), "shell", "monkey", "-p", pkg, "-c",
                         "android.intent.category.LAUNCHER", "1"], stdout=subprocess.DEVNULL)
        scrcpy = _which("scrcpy")
        if scrcpy and not opts.no_window:
            return subprocess.call([scrcpy, "--window-title", pkg, "--stay-awake"])
        return 0


BACKENDS = [MacApk(), Emulator()]


def pick(name=None):
    for b in BACKENDS:
        if (name is None or b.name == name) and b.available():
            return b
    return None

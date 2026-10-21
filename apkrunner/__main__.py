import argparse
import json
import sys

from . import __version__, backends
from .bundle import extract, merge
from .inspect import inspect

EXPLAIN = {
    "native": "Can run natively on this host (no VM, no translation).",
    "needs-translation": "Ships only ARM native code; on x86 it needs ARM translation (emulator backend provides it).",
    "unsupported": "No native libraries for any ABI this host can execute.",
}


def cmd_inspect(a):
    for path in a.apk:
        info = inspect(path, a.arch)
        if a.json:
            print(json.dumps(info, indent=2))
            continue
        print(f"{path}\n  package   : {info['package']} {info.get('version') or ''}")
        print(f"  sdk       : min {info.get('min_sdk')} target {info.get('target_sdk')}")
        print(f"  splits    : {len(info['splits'])}  dex files: {info['dex']}")
        print(f"  ABIs      : {', '.join(info['abis']) or '(none, pure Java/Kotlin)'}")
        print(f"  host      : {info['host']} -> {info['verdict']}"
              + (f" ({info['abi']})" if info['abi'] else ""))
        print(f"  {EXPLAIN[info['verdict']]}")
    return 0


def cmd_extract(a):
    print(extract(a.apk, a.out))
    return 0


def cmd_merge(a):
    for f in merge(a.apk, a.out):
        print(f)
    return 0


def cmd_apis(a):
    import io
    import zipfile
    from .bundle import split_apks
    from .dex import api_usage
    dex = []
    for data in split_apks(a.apk).values():
        z = zipfile.ZipFile(io.BytesIO(data))
        dex += [z.read(n) for n in z.namelist() if n.endswith(".dex")]
    n_classes, calls = api_usage(dex)
    if a.json:
        print(json.dumps({"classes": n_classes, "apis": dict(calls.most_common())}, indent=2))
        return 0
    print(f"{n_classes} app classes, {len(calls)} distinct framework methods used")
    by_class = {}
    for ref, c in calls.items():
        k = ref.rsplit(".", 1)[0]
        by_class[k] = by_class.get(k, 0) + c
    for k, c in sorted(by_class.items(), key=lambda x: -x[1])[:a.top]:
        print(f"  {c:6}  {k}")
    return 0


def cmd_convert(a):
    from .convert import convert
    print(convert(a.apk, a.out))
    return 0


def cmd_update(a):
    from .updater import update
    return update(force=a.force)


def cmd_preview(a):
    from types import SimpleNamespace
    info = inspect(a.apk)
    opts = SimpleNamespace(activity="-", screenshot=a.screenshot, script=None, layout=a.layout, dump=False)
    return backends.JavaRuntime().run(a.apk, info, opts)


def cmd_doctor(a):
    from .inspect import host_arch
    import platform
    print(f"host: {platform.system()} {host_arch()}")
    for b in backends.BACKENDS:
        print(f"  {b.name:9} {'available' if b.available() else 'not found'}  - {b.__doc__}")
    return 0


def cmd_run(a):
    info = inspect(a.apk)
    b = backends.pick(a.backend)
    if not b:
        print("No backend available. Run `apkrunner doctor` and see the README setup section.", file=sys.stderr)
        return 1
    print(f"Running {info['package']} with backend '{b.name}' ({info['verdict']})")
    return b.run(a.apk, info, a)


class _Step(argparse.Action):
    """Keeps --tap and --type in command-line order."""

    def __call__(self, parser, ns, value, option):
        steps = getattr(ns, self.dest, None) or []
        steps.append((option, value))
        setattr(ns, self.dest, steps)


def main(argv=None):
    p = argparse.ArgumentParser(prog="apkrunner", description="Run and inspect Android apps on desktop.")
    p.add_argument("--version", action="version", version=__version__)
    p.add_argument("--no-update-check", action="store_true", help="don't check GitHub for a newer version")
    sub = p.add_subparsers(dest="cmd", required=True)

    i = sub.add_parser("inspect", help="analyse an APK/XAPK/APKM for this host")
    i.add_argument("apk", nargs="+")
    i.add_argument("--arch", help="pretend host arch (arm64, x86_64, x86)")
    i.add_argument("--json", action="store_true")
    i.set_defaults(fn=cmd_inspect)

    e = sub.add_parser("extract", help="unpack an APK/bundle and decode AndroidManifest.xml")
    e.add_argument("apk")
    e.add_argument("-o", "--out", default="extracted")
    e.set_defaults(fn=cmd_extract)

    m = sub.add_parser("merge", help="write all split APKs of an XAPK/APKM to a folder")
    m.add_argument("apk")
    m.add_argument("-o", "--out", default="splits")
    m.set_defaults(fn=cmd_merge)

    ap = sub.add_parser("apis", help="report which Android framework APIs an app calls")
    ap.add_argument("apk")
    ap.add_argument("--top", type=int, default=25)
    ap.add_argument("--json", action="store_true")
    ap.set_defaults(fn=cmd_apis)

    c = sub.add_parser("convert", help="convert the app's DEX code to a JVM .jar (uses dex2jar)")
    c.add_argument("apk")
    c.add_argument("-o", "--out", default="app.jar")
    c.set_defaults(fn=cmd_convert)

    u = sub.add_parser("update", help="download and install the latest Apk-Runner release")
    u.add_argument("--force", action="store_true", help="reinstall even if up to date")
    u.set_defaults(fn=cmd_update)

    pv = sub.add_parser("preview", help="render one layout resource of an APK (java backend)")
    pv.add_argument("apk")
    pv.add_argument("layout", help="layout name, e.g. activity_main")
    pv.add_argument("--screenshot", help="save to this PNG instead of opening a window")
    pv.set_defaults(fn=cmd_preview)

    d = sub.add_parser("doctor", help="show which run backends are available")
    d.set_defaults(fn=cmd_doctor)

    r = sub.add_parser("run", help="install and launch an app")
    r.add_argument("apk")
    r.add_argument("--backend", choices=[b.name for b in backends.BACKENDS])
    r.add_argument("--avd", help="emulator AVD name (default: first found)")
    r.add_argument("--full", action="store_true", help="disable lite emulator profile")
    r.add_argument("--no-window", action="store_true", help="don't open the scrcpy window")
    r.add_argument("--activity", help="java backend: activity class to start")
    r.add_argument("--screenshot", help="java backend: render headless to this PNG and exit")
    r.add_argument("--dump", action="store_true", help="java backend: with --screenshot, print the view tree")
    r.add_argument("--tap", action=_Step, dest="script", metavar="X,Y", help="java backend: tap before the screenshot")
    r.add_argument("--type", action=_Step, dest="script", metavar="TEXT", help="java backend: type into the focused field")
    r.set_defaults(fn=cmd_run)

    a = p.parse_args(argv)
    from . import updater
    updater.cleanup_old()
    if not a.no_update_check and a.cmd != "update":
        updater.check_on_start()
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())

import argparse
import json
import sys

from . import __version__
from .inspect import inspect

EXPLAIN = {
    "native": "Can run natively on this host (no VM, no translation).",
    "needs-translation": "Ships only ARM native code; needs the ARM->x86 translation layer (roadmap phase 4).",
    "unsupported": "No native libraries for any ABI this host can execute.",
}


def main(argv=None):
    p = argparse.ArgumentParser(prog="apkrunner")
    p.add_argument("--version", action="version", version=__version__)
    sub = p.add_subparsers(dest="cmd", required=True)
    i = sub.add_parser("inspect", help="analyse an APK/XAPK/APKM for this host")
    i.add_argument("apk", nargs="+")
    i.add_argument("--arch", help="pretend host arch (arm64, x86_64, x86)")
    i.add_argument("--json", action="store_true")
    a = p.parse_args(argv)

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


if __name__ == "__main__":
    sys.exit(main())

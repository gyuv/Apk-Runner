import os
import sys

# Launched from Finder/Launchpad: there may be no console, and old macOS adds a -psn_ argument.
for name in ("stdout", "stderr"):
    if getattr(sys, name) is None:
        setattr(sys, name, open(os.devnull, "w"))
sys.argv = [a for a in sys.argv if not a.startswith("-psn_")]


def _report_crash():
    """Inside Apk-Runner.app there's no Terminal: log the error and show a dialog."""
    import traceback
    text = traceback.format_exc()
    log = os.path.join(os.path.expanduser("~"), ".apkrunner", "app.log")
    try:
        os.makedirs(os.path.dirname(log), exist_ok=True)
        with open(log, "a") as f:
            f.write(text + "\n")
    except OSError:
        pass
    if ".app/Contents/MacOS/" in os.path.realpath(sys.executable):
        import json
        import subprocess
        last = text.strip().splitlines()[-1]
        msg = "Apk-Runner couldn't start:\n\n%s\n\nDetails were saved to %s" % (last, log)
        subprocess.call(["osascript", "-e", "display alert %s message %s as critical"
                         % (json.dumps("Apk-Runner"), json.dumps(msg))])


try:
    from apkrunner.__main__ import main
    code = main()
except SystemExit:
    raise
except BaseException:
    _report_crash()
    raise
sys.exit(code)

import os
import sys

# Launched from Finder/Launchpad: there may be no console, and old macOS adds a -psn_ argument.
for name in ("stdout", "stderr"):
    if getattr(sys, name) is None:
        setattr(sys, name, open(os.devnull, "w"))
sys.argv = [a for a in sys.argv if not a.startswith("-psn_")]

from apkrunner.__main__ import main  # noqa: E402

sys.exit(main())

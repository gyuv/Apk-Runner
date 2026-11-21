"""Start a built apkrunner's dashboard and check it answers. Usage: smoke_ui.py <binary>"""
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.request

home = tempfile.mkdtemp()
env = dict(os.environ, HOME=home, USERPROFILE=home, APKRUNNER_NO_UPDATE_CHECK="1")
p = subprocess.Popen([sys.argv[1], "--no-update-check", "ui", "--no-browser"], env=env,
                     stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    url, deadline = None, time.time() + 60
    state = os.path.join(home, ".apkrunner", "dashboard.json")
    while time.time() < deadline and p.poll() is None:
        if os.path.exists(state):
            try:
                with open(state) as f:
                    url = json.load(f)["url"]
                break
            except ValueError:
                pass
        time.sleep(0.5)
    if not url:
        out = p.stdout.read() if p.poll() is not None else ""
        sys.exit("dashboard did not start:\n" + out)
    page = urllib.request.urlopen(url, timeout=10).read()
    assert b"Apk-Runner" in page, "unexpected page"
    print("dashboard OK:", url.split("?")[0])
finally:
    p.kill()

"""Build dist/apkrunner.pyz: a single-file app that runs with any Python 3.9+."""
import os
import shutil
import tempfile
import zipapp

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.makedirs(os.path.join(root, "dist"), exist_ok=True)
with tempfile.TemporaryDirectory() as t:
    shutil.copytree(os.path.join(root, "apkrunner"), os.path.join(t, "apkrunner"),
                    ignore=shutil.ignore_patterns("__pycache__"))
    out = os.path.join(root, "dist", "apkrunner.pyz")
    zipapp.create_archive(t, out, interpreter="/usr/bin/env python3", main="apkrunner.__main__:main")
print(out)

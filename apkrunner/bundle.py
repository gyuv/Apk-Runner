"""Unpack APKs and split bundles (XAPK / APKM / APKS)."""
import io
import os
import zipfile

from . import axml
from .xmlout import to_xml


def split_apks(path):
    """Return {name: bytes} of every APK in a bundle, or the APK itself."""
    z = zipfile.ZipFile(path)
    if "AndroidManifest.xml" in z.namelist():
        with open(path, "rb") as f:
            return {os.path.basename(path): f.read()}
    return {os.path.basename(n): z.read(n) for n in z.namelist() if n.endswith(".apk")}


def merge(path, out_dir):
    """Write every split APK of a bundle to out_dir; returns the file list."""
    os.makedirs(out_dir, exist_ok=True)
    files = []
    for name, data in split_apks(path).items():
        dest = os.path.join(out_dir, name)
        with open(dest, "wb") as f:
            f.write(data)
        files.append(dest)
    return files


def _safe_join(root, name):
    dest = os.path.realpath(os.path.join(root, name))
    if not dest.startswith(os.path.realpath(root) + os.sep):
        raise ValueError("unsafe path in archive: %s" % name)
    return dest


def extract(path, out_dir):
    """Extract every split into out_dir/<split>/ and decode its manifest."""
    for name, data in split_apks(path).items():
        root = os.path.join(out_dir, os.path.splitext(name)[0])
        os.makedirs(root, exist_ok=True)
        z = zipfile.ZipFile(io.BytesIO(data))
        for member in z.infolist():
            if member.is_dir():
                continue
            dest = _safe_join(root, member.filename)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            raw = z.read(member)
            if member.filename == "AndroidManifest.xml":
                raw = to_xml(axml.parse(raw)).encode("utf-8")
            with open(dest, "wb") as f:
                f.write(raw)
    return out_dir

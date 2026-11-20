"""Read an app's display name, launcher icon and natural orientation from its APK."""
import io
import zipfile

from . import arsc, axml
from .bundle import split_apks

# android:screenOrientation enum values
LANDSCAPE = {0, 6, 8, 11}
PORTRAIT = {1, 7, 9, 12}


def _base_apk(path):
    """ZipFile of the base APK (the split without a 'split' attribute)."""
    for data in split_apks(path).values():
        z = zipfile.ZipFile(io.BytesIO(data))
        tags = axml.parse(z.read("AndroidManifest.xml"))
        if tags and tags[0][1] == "manifest" and not tags[0][2].get("split"):
            return z, tags
    raise ValueError("no base APK found in %s" % path)


def orientation(tags):
    """'landscape', 'portrait' or None, weighting the launcher activity double."""
    votes, current = {"landscape": 0, "portrait": 0}, None
    for depth, tag, attrs in tags:
        if tag in ("activity", "activity-alias") and depth == 2:
            o = attrs.get("screenOrientation")
            o = int(o) if isinstance(o, str) and o.isdigit() else o
            side = "landscape" if o in LANDSCAPE else "portrait" if o in PORTRAIT else None
            current = (attrs, side)
            if side:
                votes[side] += 1
        elif tag == "category" and attrs.get("name") == "android.intent.category.LAUNCHER" and current:
            if current[1]:
                votes[current[1]] += 2
    if votes["landscape"] == votes["portrait"]:
        return None
    return max(votes, key=votes.get)


def _png_from(z, table, resid, depth=0):
    for path in table.files(resid):
        if path.endswith(".png") or path.endswith(".webp"):
            if path in z.namelist():
                return z.read(path)
        elif path.endswith(".xml") and depth < 3 and path in z.namelist():
            # adaptive icon: use its foreground (or background) layer
            for _, tag, attrs in axml.parse(z.read(path)):
                if tag in ("foreground", "background"):
                    rid = arsc.ref_id(attrs.get("drawable"))
                    png = rid and _png_from(z, table, rid, depth + 1)
                    if png:
                        return png
    return None


def _fallback_icon(z):
    best = None
    for n in z.namelist():
        if n.startswith("res/") and "ic_launcher" in n and n.endswith(".png"):
            info = z.getinfo(n)
            if best is None or info.file_size > best.file_size:
                best = info
    return z.read(best) if best else None


def meta(path):
    z, tags = _base_apk(path)
    pkg = tags[0][2].get("package")
    table = arsc.Table(z.read("resources.arsc")) if "resources.arsc" in z.namelist() else None
    label = icon = None
    for _, tag, attrs in tags:
        if tag == "application":
            label, icon = attrs.get("label"), attrs.get("roundIcon") or attrs.get("icon")
            break
    rid = arsc.ref_id(label)
    if rid and table:
        label = table.string(rid)
    if not isinstance(label, str) or not label.strip() or label.startswith("@"):
        label = pkg.rsplit(".", 1)[-1].capitalize()
    png = None
    rid = arsc.ref_id(icon)
    if rid and table:
        png = _png_from(z, table, rid)
    png = png or _fallback_icon(z)
    return {"package": pkg, "label": label.strip(), "icon_png": png, "orientation": orientation(tags)}

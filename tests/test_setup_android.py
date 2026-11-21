import hashlib
import http.server
import io
import os
import stat
import tempfile
import threading
import unittest
import zipfile
from unittest import mock

from apkrunner import setup_android as sa

REPO_XML = """<?xml version="1.0"?>
<sdk:sdk-repository xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03">
  <channel id="channel-0">stable</channel>
  <channel id="channel-3">canary</channel>
  <remotePackage path="platform-tools">
    <revision><major>35</major><minor>0</minor><micro>2</micro></revision>
    <channelRef ref="channel-0"/>
    <archives>
      <archive><complete><size>{pt_size}</size><checksum type="sha1">{pt_sha}</checksum><url>platform-tools-darwin.zip</url></complete><host-os>macosx</host-os></archive>
      <archive><complete><size>1</size><checksum>x</checksum><url>platform-tools-linux.zip</url></complete><host-os>linux</host-os></archive>
    </archives>
  </remotePackage>
  <remotePackage path="emulator">
    <revision><major>34</major><minor>1</minor><micro>0</micro></revision>
    <channelRef ref="channel-0"/>
    <archives>
      <archive><complete><size>1</size><checksum>old</checksum><url>emulator-old.zip</url></complete><host-os>macosx</host-os></archive>
    </archives>
  </remotePackage>
  <remotePackage path="emulator">
    <revision><major>35</major><minor>2</minor><micro>10</micro></revision>
    <channelRef ref="channel-0"/>
    <archives>
      <archive><complete><size>{em_size}</size><checksum>{em_sha}</checksum><url>emulator-darwin_x64.zip</url></complete><host-os>macosx</host-os><host-arch>x64</host-arch></archive>
      <archive><complete><size>1</size><checksum>a</checksum><url>emulator-darwin_aarch64.zip</url></complete><host-os>macosx</host-os><host-arch>aarch64</host-arch></archive>
    </archives>
  </remotePackage>
  <remotePackage path="emulator">
    <revision><major>36</major><minor>0</minor><micro>0</micro></revision>
    <channelRef ref="channel-3"/>
    <archives><archive><complete><size>1</size><checksum>c</checksum><url>emulator-canary.zip</url></complete><host-os>macosx</host-os></archive></archives>
  </remotePackage>
</sdk:sdk-repository>"""

IMG_XML = """<?xml version="1.0"?>
<sys-img:sdk-sys-img xmlns:sys-img="http://schemas.android.com/sdk/android/repo/sys-img2/03">
  <channel id="channel-0">stable</channel>
  <remotePackage path="system-images;android-30;google_apis;x86_64">
    <revision><major>12</major></revision>
    <channelRef ref="channel-0"/>
    <archives><archive><complete><size>{im_size}</size><checksum>{im_sha}</checksum><url>x86_64-30_r12.zip</url></complete></archive></archives>
  </remotePackage>
</sys-img:sdk-sys-img>"""


def _zip(entries):
    """entries: {name: (bytes, mode)}; mode 0o120777 makes a symlink."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, (data, mode) in entries.items():
            info = zipfile.ZipInfo(name)
            info.external_attr = mode << 16
            z.writestr(info, data)
    return buf.getvalue()


PT = _zip({"platform-tools/adb": (b"#!/bin/sh\n", 0o100755), "platform-tools/source.properties": (b"x", 0o100644)})
EM = _zip({"emulator/emulator": (b"#!/bin/sh\n", 0o100755), "emulator/source.properties": (b"x", 0o100644),
           "emulator/lib/libfoo.1.dylib": (b"lib", 0o100644), "emulator/lib/libfoo.dylib": (b"libfoo.1.dylib", 0o120777)})
IM = _zip({"x86_64/system.img": (b"img", 0o100644), "x86_64/source.properties": (b"x", 0o100644)})


def _fill(t):
    sha = lambda b: hashlib.sha1(b).hexdigest()
    return t.format(pt_size=len(PT), pt_sha=sha(PT), em_size=len(EM), em_sha=sha(EM),
                    im_size=len(IM), im_sha=sha(IM)).encode()


class PickTest(unittest.TestCase):
    def test_stable_newest_arch_specific(self):
        x = _fill(REPO_XML)
        e = sa.pick(x, "emulator", "macosx", "x64", "B/")
        self.assertEqual((e["url"], e["revision"]), ("B/emulator-darwin_x64.zip", (35, 2, 10)))
        self.assertEqual(sa.pick(x, "emulator", "macosx", "aarch64", "B/")["url"], "B/emulator-darwin_aarch64.zip")
        self.assertEqual(sa.pick(x, "platform-tools", "macosx", "x64", "B/")["url"], "B/platform-tools-darwin.zip")
        self.assertIsNone(sa.pick(x, "emulator", "windows", "x64", "B/"))


class SetupTest(unittest.TestCase):
    def setUp(self):
        files = {"/repository2-3.xml": _fill(REPO_XML), "/sys-img/google_apis/sys-img2-3.xml": _fill(IMG_XML),
                 "/platform-tools-darwin.zip": PT, "/emulator-darwin_x64.zip": EM,
                 "/sys-img/google_apis/x86_64-30_r12.zip": IM}

        class H(http.server.BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def do_GET(self):
                body = files.get(self.path)
                self.send_response(200 if body else 404)
                self.end_headers()
                self.wfile.write(body or b"")

        self.srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        self.srv.shutdown()
        self.srv.server_close()

    def test_full_setup(self):
        base = "http://127.0.0.1:%d/" % self.srv.server_address[1]
        env = {"ANDROID_HOME": os.path.join(self.tmp, "sdk"), "ANDROID_AVD_HOME": os.path.join(self.tmp, "avd")}
        with mock.patch.dict(os.environ, env), \
                mock.patch.object(sa, "REPO", base), mock.patch.object(sa, "REPO_LIST", base + "repository2-3.xml"), \
                mock.patch.object(sa, "SYSIMG_LIST", base + "sys-img/google_apis/sys-img2-3.xml"), \
                mock.patch.object(sa, "host", return_value=("macosx", "x64", "x86_64")):
            sa.setup(log=lambda *_: None)
            self.assertEqual(sa.PROGRESS["state"], "done")
            self.assertTrue(sa.installed())
            sdk = env["ANDROID_HOME"]
            adb = os.path.join(sdk, "platform-tools", "adb")
            if os.name != "nt":  # Windows has no executable permission bit
                self.assertTrue(os.stat(adb).st_mode & stat.S_IXUSR)
            lib = os.path.join(sdk, "emulator", "lib", "libfoo.dylib")
            if os.path.islink(lib):
                self.assertEqual(os.readlink(lib), "libfoo.1.dylib")
            else:  # no symlink rights (Windows): the target file was copied instead
                self.assertEqual(open(lib, "rb").read(), b"lib")
            cfg = open(os.path.join(env["ANDROID_AVD_HOME"], "apkrunner.avd", "config.ini")).read()
            self.assertIn("image.sysdir.1=system-images/android-30/google_apis/x86_64/", cfg)
            sa.setup(log=lambda *_: None)  # second run: nothing to download, still fine
            self.assertEqual(sa.PROGRESS["state"], "done")

    def test_bad_checksum_reports_error(self):
        base = "http://127.0.0.1:%d/" % self.srv.server_address[1]
        bad = _fill(REPO_XML).replace(hashlib.sha1(PT).hexdigest().encode(), b"0" * 40)
        with mock.patch.dict(os.environ, {"ANDROID_HOME": os.path.join(self.tmp, "sdk2")}), \
                mock.patch.object(sa, "plan", return_value=(
                    [("platform-tools", sa.pick(bad, "platform-tools", "macosx", "x64", base), "")], "x86_64")):
            with self.assertRaises(RuntimeError):
                sa.setup(log=lambda *_: None)
            self.assertEqual(sa.PROGRESS["state"], "error")
            self.assertIn("checksum", sa.PROGRESS["error"])

    def test_rejects_path_traversal(self):
        z = os.path.join(self.tmp, "evil.zip")
        with open(z, "wb") as f:
            f.write(_zip({"../evil": (b"x", 0o100644)}))
        with self.assertRaises(RuntimeError):
            sa.extract(z, os.path.join(self.tmp, "out"))


if __name__ == "__main__":
    unittest.main()

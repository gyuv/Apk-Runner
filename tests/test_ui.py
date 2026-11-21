import http.server
import threading
import unittest
import urllib.error
import urllib.request

from apkrunner import ui


class UiTest(unittest.TestCase):
    def setUp(self):
        self.srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), ui.Handler)
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.base = "http://127.0.0.1:%d" % self.srv.server_address[1]

    def tearDown(self):
        self.srv.shutdown()
        self.srv.server_close()

    def test_token_required(self):
        for path in ("/", "/api/apps"):
            with self.assertRaises(urllib.error.HTTPError) as c:
                urllib.request.urlopen(self.base + path)
            self.assertEqual(c.exception.code, 403)
        req = urllib.request.Request(self.base + "/api/open?pkg=x", method="POST")
        with self.assertRaises(urllib.error.HTTPError) as c:
            urllib.request.urlopen(req)
        self.assertEqual(c.exception.code, 403)

    def test_page_with_token(self):
        body = urllib.request.urlopen(self.base + "/?t=" + ui.TOKEN).read()
        self.assertIn(b"Apk-Runner", body)


if __name__ == "__main__":
    unittest.main()


class AutoSetupTest(unittest.TestCase):
    def test_open_waits_for_setup_then_launches(self):
        from unittest import mock
        state = {"ready": False}
        launched, started = [], []
        with mock.patch.object(ui, "_needs_setup", side_effect=lambda: not state["ready"]), \
                mock.patch.object(ui.setup_android, "start_in_background",
                                  side_effect=lambda: started.append(1) or ui.setup_android.PROGRESS.update(state="running")), \
                mock.patch.object(ui.subprocess, "Popen", side_effect=lambda *a, **k: launched.append(a[0][-1])), \
                mock.patch.object(ui.time, "sleep", side_effect=lambda s: None):
            ui.setup_android.PROGRESS.clear()
            ui.setup_android.PROGRESS["state"] = "idle"
            ui._launch("com.x")                    # user presses Open before Android is ready
            self.assertEqual(launched, [])
            self.assertIn("com.x", ui.WAITING)
            loop = threading.Thread(target=ui._auto_setup_loop, daemon=True)
            loop.start()
            deadline = __import__("time").time() + 5
            while not started and __import__("time").time() < deadline:
                pass
            self.assertEqual(len(started), 1)      # setup started without being asked
            state["ready"] = True                  # setup finished
            while not launched and __import__("time").time() < deadline:
                pass
            self.assertEqual(launched, ["com.x"])  # the queued app opened by itself
            self.assertNotIn("com.x", ui.WAITING)

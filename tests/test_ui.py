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

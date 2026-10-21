import unittest

from apkrunner.dex import _java_name, _uleb


class DexTest(unittest.TestCase):
    def test_uleb(self):
        self.assertEqual(_uleb(b"\xe5\x8e\x26", 0), (624485, 3))

    def test_java_name(self):
        self.assertEqual(_java_name("Landroid/view/View;"), "android.view.View")


if __name__ == "__main__":
    unittest.main()

import unittest

from apkrunner.inspect import verdict


class VerdictTest(unittest.TestCase):
    def test_pure_java_runs_anywhere(self):
        self.assertEqual(verdict([], "x86_64"), ("native", None))

    def test_arm_on_apple_silicon(self):
        self.assertEqual(verdict(["arm64-v8a"], "arm64"), ("native", "arm64-v8a"))

    def test_x86_lib_on_intel(self):
        self.assertEqual(verdict(["arm64-v8a", "x86_64"], "x86_64"), ("native", "x86_64"))

    def test_arm_only_on_intel(self):
        self.assertEqual(verdict(["arm64-v8a"], "x86_64"), ("needs-translation", "arm64-v8a"))


if __name__ == "__main__":
    unittest.main()

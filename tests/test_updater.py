import json
import os
import tempfile
import time
import unittest
from unittest import mock

from apkrunner import updater


class UpdaterTest(unittest.TestCase):
    def test_version_compare(self):
        self.assertGreater(updater._ver("v0.10.0"), updater._ver("0.9.9"))
        self.assertEqual(updater._ver("garbage"), ())

    def test_notice_when_newer(self):
        with tempfile.TemporaryDirectory() as t, \
                mock.patch.object(updater, "STATE", os.path.join(t, "s.json")), \
                mock.patch.object(updater, "latest", return_value={"tag_name": "v99.0.0"}), \
                mock.patch.dict(os.environ, {}, clear=False), \
                mock.patch("sys.stderr") as err:
            os.environ.pop("APKRUNNER_NO_UPDATE_CHECK", None)
            updater.check_on_start()
            self.assertIn("v99.0.0", "".join(c.args[0] for c in err.write.call_args_list))

    def test_checks_at_most_daily_and_never_raises(self):
        with tempfile.TemporaryDirectory() as t:
            state = os.path.join(t, "s.json")
            with open(state, "w") as f:
                json.dump({"checked": time.time(), "tag": "v0.0.1"}, f)
            with mock.patch.object(updater, "STATE", state), \
                    mock.patch.object(updater, "latest", side_effect=OSError("offline")) as latest:
                updater.check_on_start()
                latest.assert_not_called()
            os.remove(state)
            with mock.patch.object(updater, "STATE", state), \
                    mock.patch.object(updater, "latest", side_effect=OSError("offline")):
                updater.check_on_start()  # offline: silent


if __name__ == "__main__":
    unittest.main()

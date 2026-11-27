"""Runs the SDK against the fake `land` in fake, put first on PATH."""
import os
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from land_sdk import Land, RefusedError, StillRunningError, UsageError  # noqa: E402

LOG = os.path.join(tempfile.mkdtemp(prefix="land-sdk-"), "argv")
os.environ["PATH"] = f"{HERE.parent.parent / 'fake'}{os.pathsep}{os.environ['PATH']}"
os.environ["FAKE_LAND_LOG"] = LOG


def argv() -> str:
    return pathlib.Path(LOG).read_text().strip()


class LandTest(unittest.TestCase):
    land = Land()

    def test_merge_returns_the_answer_and_passes_json(self):
        self.assertEqual(self.land.merge(["sub/ok"], "main")["landed"], ["sub/ok"])
        self.assertEqual(argv(), "merge sub/ok --onto main --json")

    def test_exit_1_is_refused_and_keeps_the_answer(self):
        with self.assertRaises(RefusedError) as e:
            self.land.merge(["sub/bad"], "main")
        self.assertEqual(e.exception.answer["refused"][0]["branch"], "sub/bad")

    def test_exit_2_carries_fix_and_docs(self):
        with self.assertRaises(UsageError) as e:
            self.land.logs("users")
        self.assertEqual(e.exception.fix, "land merge <branch>... --onto main")
        self.assertEqual(e.exception.docs, "https://docs.useland.dev/errors#exit-2")

    def test_exit_3_is_still_running(self):
        with self.assertRaises(StillRunningError) as e:
            self.land.add()
        self.assertEqual(e.exception.fix, "land add")

    def test_a_local_run_is_never_signed(self):
        a = self.land.local()
        self.assertIs(a["signed"], False)
        self.assertEqual(a["tests"]["ran_units"][0]["name"], "spec")

    def test_a_secrets_value_goes_on_stdin(self):
        self.land.secrets.set("STRIPE_KEY", "sk_test_1")
        self.assertEqual(argv(), "secret set STRIPE_KEY --json")
        self.assertEqual(pathlib.Path(LOG + ".stdin").read_text(), "sk_test_1")


if __name__ == "__main__":
    unittest.main()

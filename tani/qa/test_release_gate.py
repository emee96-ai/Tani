#!/usr/bin/env python3
"""Exercise production version checks without contacting any external service."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class ReleaseVersionGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="tani-release-gate-test-")
        root = Path(cls.temp.name)
        keystore = root / "candidate.jks"
        subprocess.run([
            "keytool", "-genkeypair", "-keystore", str(keystore),
            "-storepass", "changeit", "-keypass", "changeit", "-alias", "test",
            "-dname", "CN=Tani gate test", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "2", "-noprompt",
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        # Reaching HTTP proves configuration validation accepted the version pair.
        # Network is deliberately unavailable; these tests never approve a release.
        curl = root / "curl"
        curl.write_text('#!/bin/sh\necho HTTP_DISABLED_FOR_TEST >&2\nexit 1\n')
        curl.chmod(0o700)
        cls.env = {key: value for key, value in os.environ.items() if not key.startswith("TANI_")}
        cls.env.update({
            "PATH": str(root) + os.pathsep + os.environ["PATH"],
            "TANI_APP_LINK_HOST": "launch.tani.sd",
            "TANI_RESET_REDIRECT": "https://launch.tani.sd/auth/reset",
            "TANI_RELEASE_STORE_FILE": str(keystore),
            "TANI_RELEASE_STORE_PASSWORD": "changeit",
            "TANI_RELEASE_KEY_PASSWORD": "changeit",
            "TANI_RELEASE_KEY_ALIAS": "test",
            "TANI_VERSION_CODE": "8",
            "TANI_ADMIN_VERSION_CODE": "8",
            "TANI_PREVIOUS_VERSION_CODE": "7",
            "TANI_PREVIOUS_ADMIN_VERSION_CODE": "7",
            "TANI_TERMS_URL": "https://launch.tani.sd/terms",
            "TANI_PRIVACY_URL": "https://launch.tani.sd/privacy",
            "TANI_ACCOUNT_DELETION_URL": "https://launch.tani.sd/account-deletion",
            "TANI_FIREBASE_API_KEY": "AIza" + "x" * 35,
            "TANI_FIREBASE_APPLICATION_ID": "1:123456:android:abcdef",
            "TANI_FIREBASE_SENDER_ID": "123456",
            "TANI_FIREBASE_PROJECT_ID": "tani-launch-test",
            "TANI_SUPABASE_SERVICE_KEY": "synthetic-test-value",
        })

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_gate(self, changes):
        env = self.env.copy()
        env.update(changes)
        gate = Path(__file__).with_name("run_release_gate.sh")
        return subprocess.run(["bash", str(gate)], env=env, capture_output=True, text=True, timeout=15)

    def assert_rejected(self, changes, message):
        result = self.run_gate(changes)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(message, result.stderr)
        self.assertNotIn("HTTP_DISABLED_FOR_TEST", result.stderr)

    def assert_version_accepted(self, changes):
        result = self.run_gate(changes)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("HTTP_DISABLED_FOR_TEST", result.stderr)
        self.assertIn("public launch page is unavailable", result.stderr)
        self.assertNotIn("PASS: production", result.stdout)

    def test_previous_codes_are_required(self):
        for key in ["TANI_PREVIOUS_VERSION_CODE", "TANI_PREVIOUS_ADMIN_VERSION_CODE"]:
            with self.subTest(key=key):
                self.assert_rejected({key: ""}, key + " must be the latest published code")

    def test_same_customer_code_is_rejected(self):
        self.assert_rejected({"TANI_PREVIOUS_VERSION_CODE": "8"}, "TANI_VERSION_CODE must exceed TANI_PREVIOUS_VERSION_CODE")

    def test_older_customer_code_is_rejected(self):
        self.assert_rejected({"TANI_PREVIOUS_VERSION_CODE": "9"}, "TANI_VERSION_CODE must exceed TANI_PREVIOUS_VERSION_CODE")

    def test_admin_code_is_checked_independently(self):
        self.assert_rejected({"TANI_PREVIOUS_ADMIN_VERSION_CODE": "8"}, "TANI_ADMIN_VERSION_CODE must exceed TANI_PREVIOUS_ADMIN_VERSION_CODE")

    def test_malformed_previous_codes_are_rejected(self):
        for value in ["-1", "01", "1e3", "2147483647", "9" * 200]:
            with self.subTest(value=value):
                self.assert_rejected({"TANI_PREVIOUS_VERSION_CODE": value}, "TANI_PREVIOUS_VERSION_CODE must be the latest published code")

    def test_invalid_new_codes_are_rejected(self):
        for value in ["0", "6", "08", "1e3", "2147483647", "9" * 200]:
            with self.subTest(value=value):
                self.assert_rejected({"TANI_VERSION_CODE": value}, "TANI_VERSION_CODE must be 7..2100000000")

    def test_increment_reaches_http_but_does_not_approve(self):
        self.assert_version_accepted({})

    def test_first_release_accepts_zero_previous_codes(self):
        self.assert_version_accepted({"TANI_PREVIOUS_VERSION_CODE": "0", "TANI_PREVIOUS_ADMIN_VERSION_CODE": "0"})

    def test_different_app_histories_are_supported(self):
        self.assert_version_accepted({"TANI_PREVIOUS_VERSION_CODE": "5", "TANI_PREVIOUS_ADMIN_VERSION_CODE": "1"})


if __name__ == "__main__":
    unittest.main(verbosity=2)

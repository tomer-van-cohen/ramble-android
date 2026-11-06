#!/usr/bin/env python3
"""Regression checks for the publication boundary; credentials below are constructed fakes."""
import importlib.util
import unittest
from pathlib import Path
from source_manifest import allowed

spec = importlib.util.spec_from_file_location('source_check', Path(__file__).with_name('check-public-source.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)

class SourceBoundaryTest(unittest.TestCase):
    def test_private_paths_are_rejected_even_inside_public_directories(self):
        for path in ('app/src/.env', 'docs/release.jks', 'scripts/cache.zip', 'app/src/main/assets/state.db',
                     'docs/.git/config', 'artifacts/log.json', 'publisher-site/index.html', '.playwright-mcp/session',
                     'docs/private-recording.m4a'):
            self.assertFalse(allowed(Path(path)), path)

    def test_source_and_explicit_synthetic_audio_remain_included(self):
        for path in ('gradle/wrapper/gradle-wrapper.jar', 'app/src/main/AndroidManifest.xml',
                     'app/src/androidTest/assets/speech/short-he.wav', 'docs/GOOGLE-DRIVE-SETUP.md'):
            self.assertTrue(allowed(Path(path)), path)

    def test_secret_and_private_inventory_checks_report_without_values(self):
        for secret in ('gsk_' + 'a' * 32, 'ghp_' + 'b' * 36, '/' + 'Users/private-owner/project', 'user' + '@mail.test'):
            matches = check.findings(Path('docs/example.md'), secret.encode())
            self.assertTrue(matches)
            self.assertNotIn(secret, repr(matches))
        self.assertFalse(check.findings(Path('docs/example.md'), b'dana@example.com'))

if __name__ == '__main__':
    unittest.main()

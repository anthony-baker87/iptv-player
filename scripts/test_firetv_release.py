import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class ReleaseManifestTest(unittest.TestCase):
    def test_manifest_identifies_the_exact_version_and_apk_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / 'fixture.apk'
            apk.write_bytes(b'release fixture, not an installable package')
            environment = dict(os.environ, FIRETV_VERSION_CODE='1042', FIRETV_VERSION_NAME='1.1.42',
                               GITHUB_REPOSITORY='anthony-baker87/iptv-player', GITHUB_SHA='fixture-commit')
            subprocess.run([sys.executable, 'scripts/firetv-release.py', '--apk', str(apk),
                            '--output', str(root / 'output'), '--manifest-only'], env=environment, check=True)
            result = json.loads((root / 'output/firetv-update.json').read_text())
            self.assertEqual(result['sha256'], hashlib.sha256(apk.read_bytes()).hexdigest())
            self.assertEqual(result['size'], apk.stat().st_size)
            self.assertEqual(result['versionCode'], 1042)
            self.assertTrue(result['apkUrl'].endswith('/firetv-v1.1.42/IPTV-FireTV-1.1.42.apk'))


if __name__ == '__main__':
    unittest.main()

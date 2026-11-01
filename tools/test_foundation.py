"""Host-side checks for M01 security boundaries and measurement accuracy."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

from apk_metrics import measure

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class FoundationContractTest(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()

    def test_minimum_network_permissions_and_no_background_components(self):
        self.assertEqual({'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'},
                         {p.get(ANDROID + 'name') for p in self.manifest.findall('uses-permission')})
        application = self.manifest.find('application')
        self.assertEqual([], application.findall('service'))
        self.assertEqual([], application.findall('receiver'))
        self.assertEqual([], application.findall('provider'))

    def test_backup_and_cleartext_disabled(self):
        application = self.manifest.find('application')
        self.assertEqual('false', application.get(ANDROID + 'allowBackup'))
        self.assertEqual('false', application.get(ANDROID + 'usesCleartextTraffic'))

    def test_android_12_backup_and_device_transfer_exclude_every_domain(self):
        rules = ET.parse(ROOT / 'app/src/main/res/xml/data_extraction_rules.xml').getroot()
        domains = {'root', 'file', 'database', 'sharedpref', 'external',
                   'device_root', 'device_file', 'device_database', 'device_sharedpref'}
        for kind in ['cloud-backup', 'device-transfer']:
            entries = rules.find(kind).findall('exclude')
            self.assertEqual(domains, {entry.get('domain') for entry in entries})
            self.assertTrue(all(entry.get('path') == '.' for entry in entries))

    def test_only_launcher_exported(self):
        components = self.manifest.find('application').findall('activity')
        self.assertEqual(1, len(components))
        self.assertEqual('.MainActivity', components[0].get(ANDROID + 'name'))
        self.assertEqual('true', components[0].get(ANDROID + 'exported'))
        action = components[0].find('intent-filter/action')
        self.assertEqual('android.intent.action.MAIN', action.get(ANDROID + 'name'))

    def test_apk_metrics_hash_and_native_entry_sizes(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w', zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('lib/arm64-v8a/libfixture.so', b'x' * 4096)
                archive.writestr('classes.dex', b'synthetic')
            result = measure(apk)
            self.assertEqual(apk.stat().st_size, result['apk_bytes'])
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), result['sha256'])
            self.assertEqual(1, len(result['native_libraries']))
            native = result['native_libraries'][0]
            self.assertEqual(4096, native['uncompressed_bytes'])
            self.assertLess(native['compressed_bytes'], 4096)

    def test_invalid_archive_is_not_reported_as_an_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / 'invalid.apk'
            apk.write_bytes(b'not a zip')
            with self.assertRaises(zipfile.BadZipFile):
                measure(apk)


if __name__ == '__main__':
    unittest.main()

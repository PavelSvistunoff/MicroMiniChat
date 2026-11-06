"""Resource completeness and formatting contracts for both supported UI languages."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'

class LocalizationContractTest(unittest.TestCase):
    def test_translation_keys_and_format_arguments_match(self):
        english = ET.parse(ROOT / 'values/strings.xml').getroot()
        ukrainian = ET.parse(ROOT / 'values-uk/strings.xml').getroot()
        expected = {e.get('name'): e.text or '' for e in english.findall('string') if e.get('translatable') != 'false'}
        actual = {e.get('name'): e.text or '' for e in ukrainian.findall('string')}
        self.assertEqual(expected.keys(), actual.keys())
        for key, value in expected.items():
            self.assertEqual(sorted(re.findall(r'%(?:\d+\$)?[sd]', value)),
                             sorted(re.findall(r'%(?:\d+\$)?[sd]', actual[key])), key)
    def test_ukrainian_plural_categories_and_declared_locales(self):
        strings = ET.parse(ROOT / 'values-uk/strings.xml').getroot()
        for plural in strings.findall('plurals'):
            self.assertEqual({'one', 'few', 'many', 'other'}, {item.get('quantity') for item in plural})
        locales = ET.parse(ROOT / 'xml/locales_config.xml').getroot()
        self.assertEqual({'en', 'uk'}, {item.get('{http://schemas.android.com/apk/res/android}name') for item in locales})

"""Test signed Tor UI on two disposable foreground emulators, without payload logs."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'io.github.pavelsvistunoff.microminichat'


class Device:
    def __init__(self, serial):
        if not serial.startswith('emulator-'):
            raise ValueError('Only disposable emulators are supported')
        self.serial = serial
        self.adb = os.environ.get('MMC_ADB', str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'))

    def run(self, *args, private_input=None):
        try:
            result = subprocess.run([self.adb, '-s', self.serial, *args], input=private_input,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                                    timeout=180 if args and args[0] in {'install', 'uninstall'} else 45)
        except subprocess.TimeoutExpired:
            raise RuntimeError('ADB timeout; arguments suppressed') from None
        if result.returncode:
            raise RuntimeError('ADB failed; arguments suppressed')
        return result.stdout

    def launch(self):
        self.run('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')

    def nodes(self):
        value = self.run('exec-out', 'uiautomator', 'dump', '/proc/self/fd/1')
        start, end = value.find('<?xml'), value.find('</hierarchy>')
        if start < 0 or end < 0:
            return []
        return list(ET.fromstring(value[start:end + 12]).iter('node'))

    def wait(self, predicate, seconds=45):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            result = predicate(self.nodes())
            if isinstance(result, ET.Element) or result:
                return result
            time.sleep(.4)
        raise RuntimeError('UI condition timed out; no screen text logged')

    def tap(self, node):
        values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
            raise RuntimeError('UI target has no visible bounds')
        left, top, right, bottom = values
        self.run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    def button(self, label):
        self.tap(self.wait(lambda ns: next((n for n in ns if n.get('text', '').casefold() == label.casefold()
                                           and n.get('enabled') == 'true'), None)))

    def visible(self, text, seconds=60):
        self.wait(lambda ns: any(text in n.get('text', '') for n in ns), seconds)

    def menu(self):
        self.tap(self.wait(lambda ns: next((n for n in ns if n.get('content-desc') == 'More options'), None)))

    def fingerprints(self):
        text = self.wait(lambda ns: next((n.get('text') for n in ns
                                         if n.get('text', '').startswith('Your fingerprint:')), None))
        match = re.fullmatch(r'Your fingerprint:\s*([A-F0-9:]+)\s*Peer fingerprint:\s*([A-F0-9:]+)', text)
        if not match:
            raise RuntimeError('Full fingerprint display unavailable')
        return match.groups()

    def diagnostic(self):
        try:
            self.menu()
            self.button('Settings')
            self.button('Connection diagnostics')
            text = self.wait(lambda ns: next((n.get('text') for n in ns
                                             if 'Last connection diagnostic:' in n.get('text', '')), None), 10)
            match = re.search(r'Last connection diagnostic: ([A-Za-z0-9_]{1,320})(?:\n|$)', text)
            self.button('Close')
            return match.group(1) if match else 'none'
        except Exception:
            return 'unavailable'

    def type_private(self, value):
        # The capability is supplied over non-PTY stdin, never a host command argument/output.
        if len(value) > 512 or not re.fullmatch(r'microminichat://pair/v2#[A-Za-z0-9_-]+', value):
            raise ValueError('Invalid synthetic invitation')
        self.run('shell', '-T', private_input='input text ' + shlex.quote(value) + '\n')


def smoke(serial, peer_serial, replace, variant):
    if serial == peer_serial or not replace:
        raise SystemExit('Two distinct disposable emulators and --replace-test-install are required')
    left, right = Device(serial), Device(peer_serial)
    stage = 'setup'
    devices = []
    try:
        for device in [left, right]:
            abi = device.run('shell', 'getprop', 'ro.product.cpu.abi').strip()
            if abi not in {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}:
                raise RuntimeError('Unsupported emulator ABI')
            apk = ROOT / f'app/build/outputs/apk/{variant}/app-{abi}-{variant}.apk'
            if 'package:' + PACKAGE in device.run('shell', 'pm', 'list', 'packages', PACKAGE).splitlines():
                device.run('uninstall', PACKAGE)
            device.run('install', '--no-streaming', '-r', str(apk))
            devices.append({'api': int(device.run('shell', 'getprop', 'ro.build.version.sdk').strip()),
                            'abi': abi, 'model': device.run('shell', 'getprop', 'ro.product.model').strip(),
                            'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest()})
            device.run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
            device.run('shell', 'wm', 'dismiss-keyguard')
            device.launch()
        stage = 'creator publication and Sharesheet'
        print('Tor UI smoke:', stage, flush=True)
        left.button('Create Chat')
        left.visible('invitation ready', 330)
        left.menu()
        left.button('Verify peer identity')
        own_text = left.wait(lambda ns: next((n.get('text') for n in ns if n.get('text', '').startswith('Your fingerprint:')), None))
        creator_fingerprint = re.match(r'Your fingerprint:\s*([A-F0-9:]+)', own_text).group(1)
        left.button('Close')
        left.button('Share Invitation')
        invitation = left.wait(lambda ns: next((n.get('text') for n in ns
                                               if n.get('text', '').startswith('microminichat://pair/v2#')), None))
        encoded = invitation.split('#', 1)[1]
        envelope = base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4))
        if len(envelope) != 138 or ':'.join(f'{byte:02X}' for byte in envelope[66:98]) != creator_fingerprint:
            raise RuntimeError('Sharesheet did not expose this creator invitation')
        left.run('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        stage = 'explicit import and authentication on two foreground devices'
        print('Tor UI smoke:', stage, flush=True)
        right.button('Join Chat')
        right.tap(right.wait(lambda ns: next((n for n in ns if n.get('resource-id', '').endswith('/pair_input')), None)))
        right.type_private(invitation)
        right.wait(lambda ns: any(n.get('resource-id', '').endswith('/pair_input') and n.get('text') == invitation for n in ns), 10)
        print('Tor UI smoke: exact invitation entered; submitting with keyboard visible', flush=True)
        right.button('Connect')
        right.wait(lambda ns: not any(n.get('resource-id', '').endswith('/pair_input') for n in ns) and bool(ns), 10)
        right.wait(lambda ns: any(n.get('text') == '[secure] compare fingerprints before messaging' for n in ns), 400)
        right.button('Verify peer identity')
        peer_own, peer_remote = right.fingerprints()
        right.button('Close')
        left.button('Verify peer identity')
        main_own, main_remote = left.fingerprints()
        if (main_own, main_remote) != (peer_remote, peer_own):
            raise RuntimeError('Mutual fingerprint comparison failed')
        left.button('I compared; they match')
        right.button('Verify peer identity')
        right.button('I compared; they match')
        for device in [left, right]:
            device.visible('[peer] connected')
        stage = 'durable native UI text acknowledgement'
        print('Tor UI smoke:', stage, flush=True)
        right.tap(right.wait(lambda ns: next((n for n in ns if n.get('resource-id', '').endswith('/chat_input')), None)))
        right.run('shell', 'input', 'text', 'tor-native-synthetic-QA')
        right.button('Send')
        right.run('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        right.visible('[status] Delivered')
        left.visible('peer: tor-native-synthetic-QA')
        stage = 'force-stop persistence and authenticated reconnect'
        print('Tor UI smoke:', stage, flush=True)
        left.run('shell', 'am', 'force-stop', PACKAGE)
        left.launch()
        left.visible('peer: tor-native-synthetic-QA')
        for device in [left, right]:
            device.visible('[peer] connected', 400)
        left.menu()
        left.button('Settings')
        left.button('Licenses')
        left.visible('GNU GENERAL PUBLIC LICENSE')
        left.button('Close')
        result = {'version': '0.1.0-tor-preview', 'build_variant': variant,
                  'environment': 'Two foreground Android emulators, separate installations, real Tor',
                  'devices': devices,
                  'physical_hardware': False, 'both_apps_foreground': True,
                  'background_policy_modified': False, 'sharesheet_and_explicit_import': True,
                  'private_invitation_not_logged': True, 'mutual_full_fingerprint_comparison': True,
                  'bilateral_verification': True, 'durable_text_ack': True,
                  'history_survives_force_stop': True, 'verified_automatic_reconnect': True,
                  'bundled_license_screen_opens': True}
        (ROOT / 'docs/TOR_UI_SMOKE.json').write_text(json.dumps(result, indent=2) + '\n')
        print('Tor UI smoke: PASS', flush=True)
    except Exception:
        print('Tor UI smoke failed at', stage, flush=True)
        print('Fixed diagnostics:', left.diagnostic(), right.diagnostic(), flush=True)
        raise
    finally:
        for device in [left, right]:
            device.run('shell', 'am', 'force-stop', PACKAGE)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True)
    parser.add_argument('--peer-device', required=True)
    parser.add_argument('--replace-test-install', action='store_true')
    parser.add_argument('--variant', choices=['debug', 'release'], default='debug')
    args = parser.parse_args()
    smoke(args.device, args.peer_device, args.replace_test_install, args.variant)

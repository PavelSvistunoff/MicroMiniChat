"""Audit local Tor APK manifests/native inventories and record hashes; never publishes."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from apk_metrics import measure

ROOT = Path(__file__).resolve().parents[1]


def audit(folder, build_tools, release=False, expected_signer=None):
    results = []
    metadata = json.loads((folder / 'output-metadata.json').read_text())
    outputs = [folder / item['outputFile'] for item in metadata['elements']]
    for abi in ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64', 'universal']:
        matches = [path for path in outputs if path.name.startswith('app-' + abi + '-')]
        if len(matches) != 1:
            raise ValueError('Expected one fresh APK per ABI; remove stale build outputs before auditing')
        apk = matches[0]
        metrics = measure(apk)
        with zipfile.ZipFile(apk) as archive:
            required = {'LICENSE', 'TOR_LICENSE.txt', 'BOUNCY_CASTLE_LICENSE.html',
                        'CONSCRYPT_LICENSE.txt', 'CONSCRYPT_NOTICE.txt', 'CONSCRYPT_NETTY_LICENSE.txt',
                        'CONSCRYPT_HARMONY_LICENSE.txt', 'BORINGSSL_LICENSE.txt', 'JTORCTL_LICENSE.txt'}
            if not {'assets/' + name for name in required}.issubset(archive.namelist()):
                raise ValueError('Missing bundled license/notice')
            for name in archive.namelist():
                if name.endswith('.dex') and b'Lorg/webrtc/' in archive.read(name):
                    raise ValueError('Obsolete WebRTC class in APK')
        manifest = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'xmltree', str(apk),
                                            'AndroidManifest.xml'], text=True)
        permissions = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'permissions', str(apk)], text=True)
        if set(re.findall("uses-permission: name='([^']+)'", permissions)) != {
                'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'}:
            raise ValueError('Unexpected APK permissions')
        if '0.1.0-tor-preview' not in manifest:
            raise ValueError('Wrong APK version')
        if release and ('TestDocumentsProvider' in manifest or re.search(r'android:debuggable.*0xffffffff', manifest)):
            raise ValueError('Debug component/flag in release')
        expected_abis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'] if abi == 'universal' else [abi]
        expected = {f'lib/{item}/{library}' for item in expected_abis
                    for library in ['libtor.so', 'libconscrypt_jni.so']}
        if {item['path'] for item in metrics['native_libraries']} != expected:
            raise ValueError('Unexpected/missing native library')
        subprocess.run([str(build_tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)], check=True,
                       stdout=subprocess.DEVNULL)
        if release and expected_signer:
            signature = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose',
                                                 '--print-certs', str(apk)], text=True)
            match = re.search(r'certificate SHA-256 digest: ([a-f0-9]{64})', signature)
            if not match or match.group(1) != expected_signer:
                raise ValueError('Unexpected release signer')
            metrics['signature_verified'] = True
            metrics['signer_certificate_sha256'] = match.group(1)
        metrics['manifest_and_native_inventory_verified'] = True
        metrics['zip_alignment_verified'] = True
        results.append(metrics)
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-tools', type=Path,
                        default=Path(os.environ.get('ANDROID_HOME', '')) / 'build-tools/35.0.0')
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/TOR_APK_METRICS.json')
    parser.add_argument('--expected-release-signer', help='Public certificate SHA256; require verified release signatures')
    args = parser.parse_args()
    result = {kind: audit(ROOT / f'app/build/outputs/apk/{kind}', args.build_tools, kind == 'release', args.expected_release_signer)
              for kind in ['debug', 'release']}
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('Verified ten local Tor APKs; metrics recorded without publication')

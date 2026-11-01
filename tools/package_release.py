"""Verify production signatures/manifests and stage five APKs plus their hashes."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from apk_metrics import measure

ROOT=Path(__file__).resolve().parents[1]
TOOLS=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
SIGNER='2e6381e1b46492b37b3f13c8ba337fac36ae49a119cfc8f9d69a928f6168df9b'


def command(*args):
    return subprocess.check_output([str(a) for a in args],text=True)


if __name__=='__main__':
    destination=ROOT/'release/0.0.1';destination.mkdir(parents=True,exist_ok=True)
    metrics=[]
    for abi in ['arm64-v8a','armeabi-v7a','x86','x86_64','universal']:
        source=ROOT/'app/build/outputs/apk/release'/f'app-{abi}-release.apk'
        signature=command(TOOLS/'apksigner','verify','--verbose','--print-certs',source)
        if SIGNER not in signature:raise SystemExit('Unexpected release signer')
        manifest=command(TOOLS/'aapt','dump','xmltree',source,'AndroidManifest.xml')
        if 'package="io.github.pavelsvistunoff.microminichat"' not in manifest or not re.search(r'android:versionName[^\n]*"0\.0\.1"',manifest):
            raise SystemExit('Unexpected release package/version')
        permissions=command(TOOLS/'aapt','dump','permissions',source)
        if set(re.findall("uses-permission: name='([^']+)'",permissions))!={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE'}:
            raise SystemExit('Unexpected release permissions')
        if 'TestDocumentsProvider' in manifest or re.search(r'android:debuggable.*0xffffffff',manifest):
            raise SystemExit('Debug fixture or debuggable flag in release')
        if abi in ['arm64-v8a','x86_64','universal']:command(TOOLS/'zipalign','-c','-P','16','4',source)
        target=destination/f'microminichat-0.0.1-{abi}.apk';shutil.copyfile(source,target)
        metrics.append(measure(target))
    (destination/'SHA256SUMS').write_text(''.join(f"{item['sha256']}  {item['apk']}\n" for item in metrics))
    (ROOT/'docs/RELEASE_APK_METRICS.json').write_text(json.dumps(metrics,indent=2)+'\n')
    print('Verified and packaged five signed 0.0.1 APKs with SHA256SUMS')

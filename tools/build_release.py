"""Build signed APKs for the configured app version using a local signer, without exposing passwords."""
from pathlib import Path
import os
import subprocess
import sys

root=Path(__file__).resolve().parents[1]
folder=root/'.release-signing'
if not (folder/'release.jks').exists() or not (folder/'password.txt').exists():
    raise SystemExit('Create or configure a release signer first; see docs/RELEASING.md')
environment=dict(os.environ)
environment['MMC_RELEASE_STORE']=str(folder/'release.jks')
environment['MMC_RELEASE_PASSWORD']=(folder/'password.txt').read_text().strip()
subprocess.run([str(root/'gradlew'),'--no-daemon',':app:assembleRelease',*sys.argv[1:]],cwd=root,env=environment,check=True)

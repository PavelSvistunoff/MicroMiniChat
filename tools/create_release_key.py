"""Create a local release signer once. Never print secrets or add them to Git."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess

root=Path(__file__).resolve().parents[1]
folder=root/'.release-signing'
folder.mkdir(mode=0o700,exist_ok=True)
os.chmod(folder,0o700)
key=folder/'release.jks'
password_file=folder/'password.txt'
if key.exists():
    if not password_file.exists():
        raise SystemExit('Existing signing key has no local password file; refusing to overwrite it')
    print('Existing local release signer retained')
else:
    if password_file.exists():
        password=password_file.read_text().strip()
    else:
        password=secrets.token_urlsafe(32)
        descriptor=os.open(password_file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(descriptor,'w') as output: output.write(password+'\n')
    java_home=os.environ.get('JAVA_HOME')
    keytool=str(Path(java_home)/'bin/keytool') if java_home else shutil.which('keytool')
    environment=dict(os.environ,MMC_RELEASE_PASSWORD=password)
    subprocess.run([keytool,'-genkeypair','-keystore',str(key),'-storetype','PKCS12',
                    '-alias','microminichat-release','-keyalg','RSA','-keysize','3072',
                    '-sigalg','SHA256withRSA','-validity','10000',
                    '-dname','CN=MicroMiniChat release',
                    '-storepass:env','MMC_RELEASE_PASSWORD','-keypass:env','MMC_RELEASE_PASSWORD'],
                   env=environment,check=True)
    os.chmod(key,0o600)
    print('Created local release signer in .release-signing; back it up securely for future updates')

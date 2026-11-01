"""Publish the reviewed 0.0.1 preview using existing GitHub credentials; never log secrets."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
REPO='PavelSvistunoff/MicroMiniChat'
TAG='v0.0.1'


def git(*arguments):
    return subprocess.check_output(['git',*arguments],cwd=ROOT,text=True).strip()


def credential():
    value=os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
    if value: return value
    environment=dict(os.environ,GIT_TERMINAL_PROMPT='0')
    result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',
                          stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,cwd=ROOT,env=environment)
    fields=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    if result.returncode or not fields.get('password'):
        raise SystemExit('No existing GitHub credential is available. Authenticate GitHub locally; do not paste a token into chat.')
    return fields['password']


def api(token,path,method='GET',body=None,missing=False):
    request=urllib.request.Request('https://api.github.com'+path,method=method,
        headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json',
                 'X-GitHub-Api-Version':'2026-03-10','User-Agent':'MicroMiniChat-release',
                 'Content-Type':'application/json'},
        data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(request,timeout=60) as response: return json.load(response)
    except urllib.error.HTTPError as error:
        if missing and error.code==404: return None
        raise SystemExit(f'GitHub API returned HTTP {error.code}; credentials were not printed') from None


def sha(path):
    result=hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda:source.read(65536),b''):result.update(chunk)
    return result.hexdigest()


def upload(token,url,path):
    parsed=urllib.parse.urlsplit(url.split('{')[0])
    if parsed.scheme!='https' or parsed.hostname!='uploads.github.com':raise SystemExit('Unexpected upload endpoint')
    connection=http.client.HTTPSConnection(parsed.hostname,timeout=120)
    target=parsed.path+'?name='+urllib.parse.quote(path.name)
    connection.putrequest('POST',target)
    for name,value in {'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json',
                       'User-Agent':'MicroMiniChat-release','X-GitHub-Api-Version':'2026-03-10',
                       'Content-Type':'application/octet-stream','Content-Length':str(path.stat().st_size)}.items():connection.putheader(name,value)
    connection.endheaders()
    with path.open('rb') as source:
        for chunk in iter(lambda:source.read(65536),b''):connection.send(chunk)
    response=connection.getresponse()
    if response.status!=201:raise SystemExit(f'Asset upload failed: HTTP {response.status}')
    result=json.loads(response.read());connection.close()
    if result.get('digest') and result['digest']!='sha256:'+sha(path):raise SystemExit('Remote asset digest mismatch')
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publish',action='store_true')
    parser.add_argument('--check',action='store_true')
    options=parser.parse_args()
    token=credential()
    repository=api(token,'/repos/'+REPO)
    if not repository.get('permissions',{}).get('push'):raise SystemExit('GitHub credential lacks push permission')
    print('GitHub authentication and repository push permission verified')
    if not options.publish:raise SystemExit(0)
    if git('branch','--show-current')!='main' or git('status','--porcelain'):raise SystemExit('Release requires a clean main checkout')
    commit=git('rev-parse','HEAD')
    source_commit=git('rev-parse',TAG+'^{commit}')
    subprocess.run(['git','merge-base','--is-ancestor',source_commit,commit],cwd=ROOT,check=True)
    allowed_files={'.gitignore','.gitattributes','AGENTS.md','README.md','RELEASE_NOTES.md',
                   'tools/github_release.py'}
    changed=git('diff','--name-only',source_commit,commit).splitlines()
    if any(path not in allowed_files and not path.startswith(('docs/','release/0.0.1/')) for path in changed):
        raise SystemExit('Changes after the release tag must contain only release artifacts, documentation or publisher configuration')
    if api(token,'/repos/'+REPO+'/commits/main')['sha']!=commit:raise SystemExit('Remote main does not match reviewed HEAD')
    files=sorted((ROOT/'release/0.0.1').glob('*.apk'))
    checksums=ROOT/'release/0.0.1/SHA256SUMS'
    if len(files)!=5 or not checksums.exists():raise SystemExit('Expected five verified APKs and SHA256SUMS')
    listed={line.split()[1]:line.split()[0] for line in checksums.read_text().splitlines()}
    if any(listed.get(path.name)!=sha(path) for path in files):raise SystemExit('Local APK checksum mismatch')
    releases=api(token,'/repos/'+REPO+'/releases?per_page=100')
    existing=next((release for release in releases if release['tag_name']==TAG),None)
    if existing and not existing['draft']:raise SystemExit('A published release already exists; refusing to replace it')
    release=existing or api(token,'/repos/'+REPO+'/releases','POST',{
        'tag_name':TAG,'target_commitish':source_commit,'name':'MicroMiniChat 0.0.1 preview',
        'body':(ROOT/'RELEASE_NOTES.md').read_text(),'draft':True,'prerelease':True})
    current={asset['name']:asset for asset in release['assets']}
    for path in [*files,checksums]:
        if path.name in current:
            if current[path.name].get('digest')!='sha256:'+sha(path):raise SystemExit('Existing draft asset differs; review required')
        else:upload(token,release['upload_url'],path)
        print('Verified uploaded asset:',path.name)
    result=api(token,'/repos/'+REPO+'/releases/'+str(release['id']),'PATCH',{'draft':False,'prerelease':True})
    print('Published:',result['html_url'])

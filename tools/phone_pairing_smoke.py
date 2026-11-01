"""Test signed phone-hosted pairing across two UIDs on a disposable emulator; no payload logs."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
MAIN='io.github.pavelsvistunoff.microminichat'
PEER=MAIN+'.qapeer'
ACTIVITY=MAIN+'.MainActivity'


def smoke(serial,replace):
    if not serial.startswith('emulator-') or not replace:
        raise SystemExit('Use a disposable emulator and --replace-test-install; never clear a real user device')
    adb=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb'
    def run(*args):
        result=subprocess.run([str(adb),'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=45)
        if result.returncode:raise RuntimeError('ADB operation failed; sensitive arguments suppressed')
        return result.stdout
    def launch(package):run('shell','am','start','-W','-n',package+'/'+ACTIVITY)
    def nodes():
        text=run('exec-out','uiautomator','dump','/proc/self/fd/1')
        start=text.find('<?xml');end=text.find('</hierarchy>')
        if start<0 or end<0:raise RuntimeError('UI hierarchy unavailable')
        return list(ET.fromstring(text[start:end+12]).iter('node'))
    def tap(node):
        bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        if len(bounds)!=4:raise RuntimeError('UI bounds unavailable')
        x1,y1,x2,y2=bounds;run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    def find(predicate,seconds=40,scroll=False):
        end=time.monotonic()+seconds;attempt=0
        while time.monotonic()<end:
            ns=nodes();result=predicate(ns)
            if isinstance(result,ET.Element) or result:return result
            if any('inputmethod' in node.get('package','') for node in ns):run('shell','input','keyevent','KEYCODE_BACK')
            elif scroll:
                view=next((node for node in ns if node.get('class')=='android.widget.ScrollView' and node.get('scrollable')=='true'),None)
                if view is not None:
                    x1,y1,x2,y2=map(int,re.findall(r'\d+',view.get('bounds')))
                    low=y1+(y2-y1)*3//4;high=y1+(y2-y1)//4
                    if attempt%8>=4:low,high=high,low
                    run('shell','input','swipe',str((x1+x2)//2),str(low),str((x1+x2)//2),str(high),'200')
            attempt+=1;time.sleep(.2)
        raise RuntimeError('Signed phone-pairing smoke timed out at '+stage[0])
    def button(label):
        tap(find(lambda ns:next((n for n in ns if n.get('text','').casefold()==label.casefold()
            and n.get('clickable')=='true' and n.get('enabled')=='true'),None),scroll=True))
    def visible(value):find(lambda ns:any(value in n.get('text','') for n in ns),scroll=True)
    def fingerprints():
        text=find(lambda ns:next((n.get('text') for n in ns if n.get('text','').startswith('Your fingerprint:')),None),scroll=True)
        match=re.fullmatch(r'Your fingerprint:\s*([A-F0-9:]+)\s*Friend’s fingerprint:\s*([A-F0-9:]+)',text)
        if not match:raise RuntimeError('Full fingerprint display unavailable')
        return match.group(1),match.group(2)
    def step(value):stage[0]=value;print('Phone pairing smoke:',value,flush=True)
    stage=['setup'];freezer=run('shell','settings','get','global','cached_apps_freezer').strip()
    try:
        run('shell','settings','put','global','cached_apps_freezer','disabled')
        for package in [MAIN,PEER]:
            if 'package:'+package in run('shell','pm','list','packages',package).splitlines():run('uninstall',package)
        run('install','-r',str(ROOT/'release/0.0.3-preview/microminichat-0.0.3-preview-arm64-v8a.apk'))
        run('install','-r',str(ROOT/'build/qa-peer/outputs/apk/release/app-arm64-v8a-release.apk'))
        run('shell','input','keyevent','KEYCODE_WAKEUP');run('shell','wm','dismiss-keyguard')
        step('signed apps installed; creating phone invitation')
        launch(MAIN);button('Pair / verify');button('Start chat');visible('Invitation ready')
        button('Copy invitation')
        step('creator TLS listener ready; joining via clipboard invitation')
        launch(PEER);button('Pair / verify');button('Join chat');button('Paste & connect');visible('Connected')
        peer_own,peer_remote=fingerprints()
        launch(MAIN);visible('Connected');main_own,main_remote=fingerprints()
        if main_own!=peer_remote or main_remote!=peer_own:raise RuntimeError('Persistent peer fingerprint mismatch')
        step('automatic TLS offer/answer completed; comparing full identities')
        button('Compare fingerprints');button('I compared; they match');button('Back to chat')
        launch(PEER);button('Compare fingerprints');button('I compared; they match');button('Back to chat')
        field=find(lambda ns:next((n for n in ns if n.get('resource-id','').endswith('/chat_input')),None))
        find(lambda ns:any(n.get('resource-id','').endswith('/send') and n.get('enabled')=='true' for n in ns))
        tap(field);run('shell','input','text','phone-pairing-release-QA');button('Send')
        visible('Me [Delivered]: phone-pairing-release-QA');launch(MAIN);visible('Peer: phone-pairing-release-QA')
        step('cross-UID text persisted and acknowledged')
        run('shell','am','force-stop',MAIN);launch(MAIN);visible('Peer: phone-pairing-release-QA');visible('Disconnected')
        button('Pair / verify');button('Start chat');visible('Invitation ready')
        result={'version':'0.0.3-preview','environment':'API 35 arm64 emulator; two signed app UIDs; cached-app freezer temporarily disabled to model two foreground phones',
            'physical_hardware':False,'internet_connectivity_tested':False,'turn_tested':False,
            'creator_tls_listener_started':True,'invitation_copied_without_payload_logging':True,
            'automatic_offer_answer_exchange':True,'full_mutual_fingerprint_comparison':True,
            'durable_message_acknowledgement':True,'history_survives_force_stop':True,
            'no_automatic_reconnect_after_force_stop':True,'new_listener_after_restart':True}
        (ROOT/'docs/PHONE_PAIRING_SMOKE.json').write_text(json.dumps(result,indent=2)+'\n')
        step('force-stop preserved history; fresh listener can start')
    finally:
        if freezer=='null':run('shell','settings','delete','global','cached_apps_freezer')
        else:run('shell','settings','put','global','cached_apps_freezer',freezer)
        run('shell','am','force-stop',MAIN)
        if 'package:'+PEER in run('shell','pm','list','packages',PEER).splitlines():run('uninstall',PEER)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device',required=True)
    parser.add_argument('--replace-test-install',action='store_true')
    args=parser.parse_args();smoke(args.device,args.replace_test_install)

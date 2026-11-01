"""Two signed release app processes on a disposable emulator; no signaling/text is logged."""
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
    sdk=Path(os.environ['ANDROID_HOME'])
    adb=sdk/'platform-tools/adb'
    def run(*arguments,timeout=45):
        result=subprocess.run([str(adb),'-s',serial,*arguments],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=timeout)
        if result.returncode:raise RuntimeError('ADB command failed (sensitive arguments suppressed)')
        return result.stdout
    def launch(package,payload=None,wait=False):
        args=['shell','am','start']+(['-W'] if wait else [])+['-n',package+'/'+ACTIVITY]
        if payload is not None:args+=['-a','android.intent.action.SEND','-t','text/plain','--es','android.intent.extra.TEXT',payload]
        return run(*args)
    def nodes():
        # Write to the process pipe, not an on-device diagnostic file containing SDP.
        data=run('exec-out','uiautomator','dump','/proc/self/fd/1')
        start=data.find('<?xml');end=data.find('</hierarchy>')
        if start<0 or end<0:raise RuntimeError('UI hierarchy unavailable')
        return list(ET.fromstring(data[start:end+12]).iter('node'))
    def wait_for(predicate,seconds=45):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            result=predicate(nodes())
            if isinstance(result,ET.Element) or result:return result
            time.sleep(.2)
        raise RuntimeError('Release smoke timed out at '+stage[0])
    def tap_node(node):
        bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        if len(bounds)!=4:raise RuntimeError('UI element bounds unavailable')
        run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
    def button(text):
        print('Release smoke: tap '+text,flush=True)
        for attempt in range(12):
            ns=nodes()
            found=next((n for n in ns if n.get('text','').casefold()==text.casefold() and n.get('clickable')=='true' and n.get('enabled')=='true'),None)
            if found is not None:tap_node(found);return
            if any('inputmethod' in n.get('package','') for n in ns):
                run('shell','input','keyevent','KEYCODE_BACK');continue
            scroll=next((n for n in ns if n.get('scrollable')=='true'),None)
            if scroll is not None:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
                low=y1+(y2-y1)*3//4;high=y1+(y2-y1)//4
                if attempt%6>=3:low,high=high,low
                run('shell','input','swipe',str((x1+x2)//2),str(low),str((x1+x2)//2),str(high),'250')
            else:time.sleep(.2)
        raise RuntimeError('Release smoke control unavailable: '+text)
    def seek(predicate,direction):
        for attempt in range(18):
            ns=nodes();value=predicate(ns)
            if isinstance(value,ET.Element) or value:return value
            scroll=next((n for n in ns if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true'),None)
            if scroll is not None:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
                low=y1+(y2-y1)*3//4;high=y1+(y2-y1)//4
                down=(direction=='down')==(attempt%8<4)
                if not down:low,high=high,low
                run('shell','input','swipe',str((x1+x2)//2),str(low),str((x1+x2)//2),str(high),'250')
            else:time.sleep(.2)
        raise RuntimeError('Release smoke could not locate expected content at '+stage[0])
    def local_signal():
        return seek(lambda ns:next((n.get('text') for n in ns if n.get('class')=='android.widget.TextView' and n.get('text','').startswith('MMC1:')),None),'down')
    def fingerprints():
        value=seek(lambda ns:next((n.get('text') for n in ns if n.get('text','').startswith('My fingerprint:')),None),'up')
        match=re.search(r'My fingerprint:\s*([A-F0-9:]+)\s*Peer fingerprint:\s*([A-F0-9:]*)',value)
        if not match:raise RuntimeError('Fingerprint display unavailable')
        return match.group(1),match.group(2)
    def text_visible(value):return seek(lambda ns:any(value in n.get('text','') for n in ns),'up')
    def step(value):stage[0]=value;print('Release smoke:',value,flush=True)
    stage=['setup']
    freezer=run('shell','settings','get','global','cached_apps_freezer').strip()
    try:
        # Both app processes model two foreground phones; this is not a background-delivery test.
        run('shell','settings','put','global','cached_apps_freezer','disabled')
        if 'package:'+MAIN in run('shell','pm','list','packages',MAIN).splitlines():run('uninstall',MAIN)
        if 'package:'+PEER in run('shell','pm','list','packages',PEER):run('uninstall',PEER)
        run('install','-r',str(ROOT/'release/0.0.1/microminichat-0.0.1-arm64-v8a.apk'))
        run('install','-r',str(ROOT/'build/qa-peer/outputs/apk/release/app-arm64-v8a-release.apk'))
        run('shell','input','keyevent','KEYCODE_WAKEUP');run('shell','wm','dismiss-keyguard')
        step('signed apps installed')
        startup=launch(MAIN,wait=True);match=re.search(r'TotalTime:\s*(\d+)',startup)
        result={'environment':'API 35 arm64 emulator; two signed release app UIDs; cached-app freezer disabled for transport QA',
                'physical_hardware':False,'cold_start_total_ms':int(match.group(1)) if match else None}
        button('Pair / verify');button('Create offer');offer=local_signal();first,_=fingerprints()
        step('release offer generated')
        launch(PEER,offer);button('Apply offer / answer');answer=local_signal();second,peer_view= fingerprints()
        if peer_view!=first:raise RuntimeError('Offer fingerprint binding failed')
        launch(MAIN,answer);button('Apply offer / answer');text_visible('Connected')
        own,main_view=fingerprints()
        if own!=first or main_view!=second:raise RuntimeError('Answer fingerprint binding failed')
        button('Verify peer fingerprint');button('I compared; they match');button('Close')
        launch(PEER);text_visible('Connected');button('Verify peer fingerprint');button('I compared; they match');button('Close')
        wait_for(lambda ns:any(n.get('resource-id','').endswith('/send') and n.get('enabled')=='true' for n in ns))
        step('DTLS identities independently compared and verified')
        field=wait_for(lambda ns:next((n for n in ns if n.get('resource-id','').endswith('/chat_input')),None))
        tap_node(field);run('shell','input','text','release-QA-message-0.0.1');button('Send')
        launch(MAIN);text_visible('Peer: release-QA-message-0.0.1')
        launch(PEER);text_visible('Me [Delivered]: release-QA-message-0.0.1')
        step('cross-process persisted message acknowledged')
        run('shell','am','force-stop',MAIN);launch(MAIN);text_visible('Peer: release-QA-message-0.0.1');text_visible('Disconnected')
        button('Pair / verify');button('Create offer');local_signal();restored,_=fingerprints()
        if restored!=first:raise RuntimeError('Persistent identity changed across process death')
        result.update({'release_native_pairing':True,'mutual_fingerprint_verification':True,
                       'cross_process_message_delivered':True,'history_survives_force_stop':True,
                       'identity_survives_force_stop':True,'no_automatic_reconnect_after_force_stop':True})
        (ROOT/'docs/RELEASE_SMOKE_RESULTS.json').write_text(json.dumps(result,indent=2)+'\n')
        step('force-stop/restart preserved identity and history; no auto reconnect')
    finally:
        if freezer=='null':run('shell','settings','delete','global','cached_apps_freezer')
        else:run('shell','settings','put','global','cached_apps_freezer',freezer)
        run('shell','am','force-stop',MAIN)
        if 'package:'+PEER in run('shell','pm','list','packages',PEER):run('uninstall',PEER)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device',required=True)
    parser.add_argument('--replace-test-install',action='store_true')
    args=parser.parse_args();smoke(args.device,args.replace_test_install)

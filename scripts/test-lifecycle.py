#!/usr/bin/env python3
"""Check upgrade retention and drafts on a dedicated Android test device."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

parser=argparse.ArgumentParser()
parser.add_argument('--adb',default=os.environ.get('ADB','adb'))
parser.add_argument('--serial',default=os.environ.get('ANDROID_SERIAL','emulator-5580'))
parser.add_argument('--allow-test-device',action='store_true',required=True)
parser.add_argument('--apk')
parser.add_argument('--old-apk')
parser.add_argument('--reset',action='store_true',help='Erase AeroLedger on this disposable test device')
args=parser.parse_args()
package='cn.edu.buaa.ledger'
root=pathlib.Path(__file__).resolve().parent.parent

def adb(*command,check=True):
    result=subprocess.run([args.adb,'-s',args.serial,*command],capture_output=True,timeout=90)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace') or result.stdout.decode(errors='replace'))
    return result.stdout

def tree():
    for _ in range(4):
        adb('shell','uiautomator','dump','/data/local/tmp/aeroledger-lifecycle.xml')
        try:return ET.fromstring(adb('shell','cat','/data/local/tmp/aeroledger-lifecycle.xml'))
        except ET.ParseError:time.sleep(.3)
    raise RuntimeError('Accessibility hierarchy unavailable')

def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)

def click(value,attribute='text'):
    for _ in range(10):
        node=next((n for n in tree().iter('node') if n.attrib.get(attribute)==value),None)
        if node is not None:tap(node);return
        size=adb('shell','wm','size').decode()
        width,height=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
        adb('shell','input','swipe',str(width//2),str(int(height*.72)),str(width//2),str(int(height*.32)),'220')
        time.sleep(.3)
    raise AssertionError('Missing control: '+value)

def launch():
    adb('shell','am','start','-n',package+'/.MainActivity');time.sleep(1)

def scroll_top():
    width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size').decode())[-1])
    for _ in range(4):adb('shell','input','swipe',str(width//2),str(int(height*.35)),str(width//2),str(int(height*.8)),'180')
    time.sleep(.3)

def kill_background():
    adb('shell','input','keyevent','3');time.sleep(1.2)
    adb('shell','am','kill',package)
    for _ in range(8):
        if not adb('shell','pidof',package,check=False).strip():return
        time.sleep(.3)
    pids=adb('shell','pidof',package,check=False).decode().split()
    for pid in pids:
        assert pid.isdigit()
        # The OS may finish a cached process between pidof and kill.
        adb('shell','run-as',package,'kill','-9',pid,check=False)
    time.sleep(.5)
    assert not adb('shell','pidof',package,check=False).strip(),'App did not stop'

def snapshot():
    kill_background()
    with tempfile.TemporaryDirectory(prefix='aeroledger-db-check-') as folder:
        path=pathlib.Path(folder)/'campus-ledger-v2.db'
        for suffix in ('','-wal','-shm'):
            value=adb('exec-out','run-as',package,'cat','databases/campus-ledger-v2.db'+suffix,check=False)
            if value and not value.startswith(b'cat:'):pathlib.Path(str(path)+suffix).write_bytes(value)
        db=sqlite3.connect(path)
        try:
            content={name:db.execute('SELECT * FROM '+name+' ORDER BY 1,2').fetchall()
                     for name in ('transactions','monthly_budgets','payment_plans','monthly_reviews','metadata','import_rows')}
            result={'counts':{key:len(rows) for key,rows in content.items()},
                    'sha256':hashlib.sha256(json.dumps(content,ensure_ascii=False).encode()).hexdigest()}
        finally:db.close()
    launch();return result

if args.old_apk and not(args.reset and args.apk):parser.error('--old-apk requires --reset and --apk')
if args.reset:adb('uninstall',package,check=False)
if args.old_apk:
    adb('install',args.old_apk);launch();click('载入校园示例');click('载入示例');time.sleep(.5)
    scroll_top();text=ET.tostring(tree(),encoding='unicode')
    assert '205.40' in text and '2500.00' in text,'Old version demo data did not load'
if args.apk:adb('install','-r',args.apk)
adb('shell','am','force-stop',package);launch()
if args.old_apk:
    scroll_top();text=ET.tostring(tree(),encoding='unicode')
    assert '205.40' in text and '2500.00' in text,'Upgrade changed original totals'
before=snapshot()
if args.old_apk:assert before['counts']['transactions']==20 and before['counts']['monthly_budgets']==1
click('记一笔','content-desc')
field=next(n for n in tree().iter('node') if n.attrib.get('class')=='android.widget.EditText')
tap(field);adb('shell','input','keyevent','123',*(['67']*20));adb('shell','input','text','12.34');time.sleep(.5)
kill_background()
launch()
assert '12.34' in ET.tostring(tree(),encoding='unicode'),'Draft lost after process recreation'

def close_editor(choice):
    for _ in range(3):
        adb('shell','input','keyevent','4');time.sleep(.35)
        if '还没有保存这次编辑' in ET.tostring(tree(),encoding='unicode'):click(choice);return
    raise AssertionError('Unsaved edits were not protected')

close_editor('保留草稿并退出');click('记一笔','content-desc')
assert '12.34' in ET.tostring(tree(),encoding='unicode'),'Persistent draft did not resume'
close_editor('放弃更改')
after=snapshot();assert before==after,'Draft editing changed formal ledger data'
report={'api':adb('shell','getprop','ro.build.version.sdk').decode().strip(),
        'upgrade_from_v1':bool(args.old_apk),'background_process_recreation':'passed','unsaved_exit_protection':'passed',
        'persistent_draft_resume':'passed','discard_without_accounting':'passed','ledger_unchanged':True,'snapshot':after}
destination=root/'artifacts/tests'/('lifecycle-api-'+report['api']+'.json')
destination.parent.mkdir(parents=True,exist_ok=True)
destination.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))

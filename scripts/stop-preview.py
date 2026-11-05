#!/usr/bin/env python3
"""Stop only this project's viewer and the emulator started by its launcher."""
import json
import os
import pathlib
import re
import signal
import shutil
import subprocess
import tempfile
import urllib.request

runtime=pathlib.Path(tempfile.gettempdir())/'buaa-ledger-preview'
pid_file=runtime/'server.pid'
metadata=json.loads((runtime/'server.json').read_text()) if (runtime/'server.json').exists() else {}
if pid_file.exists():
    pid=int(pid_file.read_text());stopped=False
    try:
        url='http://127.0.0.1:%d'%metadata.get('port',18880)
        with urllib.request.urlopen(url+'/health',timeout=2) as response:health=json.load(response)
        if health.get('pid')==pid:
            with urllib.request.urlopen(url,timeout=2) as response:page=response.read().decode()
            key=re.search(r"const key='([^']+)'",page).group(1)
            request=urllib.request.Request(url+'/shutdown',data=b'{}',headers={'X-Preview-Key':key,'Content-Type':'application/json'})
            with urllib.request.urlopen(request,timeout=3) as response:response.read()
            stopped=True
    except Exception:
        pass
    cmdline=pathlib.Path('/proc')/str(pid)/'cmdline'
    if not stopped and cmdline.exists() and b'preview-server.py' in cmdline.read_bytes():os.kill(pid,signal.SIGTERM)
    pid_file.unlink(missing_ok=True)
native=runtime/'emulator.pid'
if native.exists():
    serial=metadata.get('serial','')
    adb=shutil.which('adb')
    if adb and serial.startswith('emulator-'):subprocess.run([adb,'-s',serial,'emu','kill'],capture_output=True)
    native.unlink(missing_ok=True)
if shutil.which('docker'):
    subprocess.run(['docker','-H','unix:///var/run/docker.sock','stop','--timeout','10','buaa-ledger-emulator'],capture_output=True)
print('AeroLedger preview stopped.')

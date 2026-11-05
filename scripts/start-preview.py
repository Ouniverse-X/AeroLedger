#!/usr/bin/env python3
"""Start this project's Android emulator and its loopback-only interactive viewer."""
import json
import hashlib
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
runtime = pathlib.Path(tempfile.gettempdir()) / 'buaa-ledger-preview'
runtime.mkdir(exist_ok=True)
sdk_path = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
properties = root / 'local.properties'
if not sdk_path and properties.exists():
    for line in properties.read_text().splitlines():
        if line.startswith('sdk.dir='):
            sdk_path = line.split('=', 1)[1]
sdk = pathlib.Path(sdk_path).expanduser() if sdk_path else None
adb = str(sdk / 'platform-tools' / ('adb.exe' if os.name=='nt' else 'adb')) if sdk else shutil.which('adb')
serial = os.environ.get('ANDROID_SERIAL', 'emulator-5580')
port = int(os.environ.get('LEDGER_PREVIEW_PORT', '18880'))
if not adb or not pathlib.Path(adb).exists():
    sys.exit('未找到 adb：请先在 Android Studio 中配置 SDK，或设置 ANDROID_SDK_ROOT。')
ffmpeg = os.environ.get('FFMPEG') or shutil.which('ffmpeg')
if not ffmpeg:
    sys.exit('未找到 ffmpeg：请安装 FFmpeg，或将 FFMPEG 设为可执行文件路径。')
scrcpy = runtime / 'scrcpy-server-v3.3.3'
scrcpy_hash = '7e70323ba7f259649dd4acce97ac4fefbae8102b2c6d91e2e7be613fd5354be0'
if not scrcpy.exists() or hashlib.sha256(scrcpy.read_bytes()).hexdigest() != scrcpy_hash:
    print('下载低延迟画面服务（scrcpy 3.3.3）…', flush=True)
    try:
        with urllib.request.urlopen('https://github.com/Genymobile/scrcpy/releases/download/v3.3.3/scrcpy-server-v3.3.3', timeout=30) as response:
            data = response.read(1024 * 1024)
        if hashlib.sha256(data).hexdigest() != scrcpy_hash:
            sys.exit('画面服务校验失败，请重新运行。')
        scrcpy.write_bytes(data)
    except OSError as error:
        sys.exit('下载画面服务失败：' + str(error))


def run(*command, timeout=30, check=True):
    return subprocess.run(list(command), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=timeout, check=check).stdout.decode(errors='replace').strip()


def booted():
    try:
        return run(adb, '-s', serial, 'shell', 'getprop', 'sys.boot_completed', timeout=5) == '1'
    except (subprocess.SubprocessError, OSError):
        return False

if 'ANDROID_SERIAL' not in os.environ:
    devices=[line.split()[0] for line in run(adb,'devices').splitlines()[1:] if len(line.split())>=2 and line.split()[1]=='device']
    if serial not in devices:
        if len(devices)==1:serial=devices[0]
        elif len(devices)>1:sys.exit('检测到多个设备，请设置 ANDROID_SERIAL 选择：'+', '.join(devices))


if not booted():
    avds = pathlib.Path.home() / '.android/avd'
    emulator = sdk / 'emulator' / ('emulator.exe' if os.name=='nt' else 'emulator') if sdk else None
    names=sorted(path.stem for path in avds.glob('*.ini'))
    avd_name=os.environ.get('LEDGER_AVD') or ('BuaaLedgerTest' if 'BuaaLedgerTest' in names else names[0] if len(names)==1 else None)
    if not emulator or not emulator.exists() or not avd_name or not (avds / (avd_name+'.ini')).exists():
        sys.exit('请先在 Android Studio 中启动安卓模拟器，再设置 ANDROID_SERIAL 后运行本脚本。')
    docker = ['docker', '-H', 'unix:///var/run/docker.sock']
    try:
        needs_container = pathlib.Path('/dev/kvm').exists() and not os.access('/dev/kvm',os.R_OK|os.W_OK)
        emulator_port=serial.split('-')[-1] if serial.startswith('emulator-') else '5580'
        command=[str(emulator),'-avd',avd_name,'-no-window','-no-audio','-no-boot-anim','-no-snapshot',
                 '-gpu','auto','-memory','2048','-cores','2','-port',emulator_port]
        if not needs_container:
            with (runtime/'emulator.log').open('ab') as log:
                process=subprocess.Popen(command,stdout=log,stderr=log,start_new_session=True)
                (runtime/'emulator.pid').write_text(str(process.pid))
        elif run(*docker, 'inspect', '-f', '{{.State.Running}}', 'buaa-ledger-emulator', check=False) != 'true':
            user_dir = runtime / 'android-user'
            user_dir.mkdir(exist_ok=True)
            run(*docker, 'run', '-d', '--rm', '--name', 'buaa-ledger-emulator', '--device=/dev/kvm',
                '--user', '%s:%s' % (os.getuid(), os.getgid()), '--group-add', str(pathlib.Path('/dev/kvm').stat().st_gid),
                '--network', 'host', '-e', 'ANDROID_AVD_HOME=' + str(avds), '-e', 'ANDROID_USER_HOME=/tmp/ledger-android',
                '-v', '%s:%s:ro' % (sdk, sdk), '-v', '%s:%s' % (avds, avds),
                '-v', str(user_dir) + ':/tmp/ledger-android',
                '-v', '/usr/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:ro',
                'ubuntu:22.04', str(emulator), '-avd', avd_name, '-no-window', '-no-audio',
                '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader', '-feature', '-Vulkan', '-accel', 'on',
                '-memory', '2048', '-cores', '2', '-port', emulator_port)
    except (subprocess.SubprocessError, OSError) as error:
        sys.exit('模拟器启动失败。也可在 Android Studio 中启动设备，再运行本脚本：' + str(error))
    print('等待安卓模拟器启动…', flush=True)
    deadline = time.monotonic() + 150
    while not booted():
        if time.monotonic() > deadline:
            sys.exit('设备启动超时，请检查模拟器后重试。')
        time.sleep(2)

apk = pathlib.Path(os.environ['LEDGER_PREVIEW_APK']).expanduser() if os.environ.get('LEDGER_PREVIEW_APK') else root/'releases/AeroLedger-v1.1.apk'
if not apk.exists():sys.exit('未找到安装包。请设置 LEDGER_PREVIEW_APK，或先构建 app。')
print('安装并打开航小账…', flush=True)
try:run(adb, '-s', serial, 'install', '-r', str(apk), timeout=90)
except subprocess.CalledProcessError:sys.exit('APK 安装失败。请检查设备授权及签名是否匹配；正式签名版与课程版需要通过完整备份迁移，脚本不会卸载现有应用。')
run(adb, '-s', serial, 'shell', 'cmd', 'alarm', 'set-timezone', 'Asia/Shanghai', check=False)
run(adb, '-s', serial, 'shell', 'am', 'start', '-n', 'cn.edu.buaa.ledger/.MainActivity')
url = 'http://127.0.0.1:%d' % port


def viewer_ready():
    try:
        with urllib.request.urlopen(url + '/health', timeout=5) as response:
            data = json.load(response)
            return data.get('ready') is True and data.get('version') == 3 and data.get('serial') == serial
    except Exception:
        return False


if not viewer_ready():
    pid_file = runtime / 'server.pid'
    if pid_file.exists():
        old_pid = int(pid_file.read_text())
        cmdline = pathlib.Path('/proc') / str(old_pid) / 'cmdline'
        if cmdline.exists() and str(root / 'scripts/preview-server.py').encode() in cmdline.read_bytes():
            import signal
            os.kill(old_pid, signal.SIGTERM)
            for _ in range(40):
                if not cmdline.exists():
                    break
                time.sleep(.1)
    with (runtime / 'server.log').open('ab') as log:
        process = subprocess.Popen([sys.executable, str(root / 'scripts/preview-server.py'), '--adb', adb,
                                    '--serial', serial, '--port', str(port), '--scrcpy-server', str(scrcpy),
                                    '--ffmpeg', ffmpeg], stdout=log, stderr=log,
                                   start_new_session=True)
        (runtime / 'server.pid').write_text(str(process.pid))
        (runtime / 'server.json').write_text(json.dumps({'pid':process.pid,'port':port,'serial':serial}))
    for _ in range(60):
        if viewer_ready():
            break
        time.sleep(.5)
    else:
        sys.exit('预览服务未就绪，请查看 ' + str(runtime / 'server.log'))
print('实时交互地址：' + url, flush=True)
print('远程 SSH / VS Code 环境请转发端口 %d，再在本机浏览器打开。' % port, flush=True)

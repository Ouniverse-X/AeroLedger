#!/usr/bin/env python3
"""Loopback viewer using one persistent scrcpy video and control connection."""
import argparse
import collections
import json
import math
import os
import pathlib
import secrets
import signal
import socket
import struct
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', default='emulator-5580')
parser.add_argument('--port', type=int, default=18880)
parser.add_argument('--scrcpy-server', required=True)
parser.add_argument('--ffmpeg', default='ffmpeg')
args = parser.parse_args()
token = secrets.token_urlsafe(24)
html_file = pathlib.Path(__file__).with_name('preview.html')


def adb(*command):
    result = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True, timeout=15)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace').strip() or '设备暂未就绪')
    return result.stdout


def read_exact(connection, count):
    data = bytearray()
    while len(data) < count:
        chunk = connection.recv(count - len(data))
        if not chunk:
            raise ConnectionError('设备连接已断开')
        data.extend(chunk)
    return bytes(data)


class Device:
    def __init__(self):
        self.condition = threading.Condition()
        self.control_lock = threading.Lock()
        self.control = None
        self.frame = b''
        self.frame_at = 0.0
        self.sequence = 0
        self.dimensions = (720, 1600)
        self.frames_at = collections.deque(maxlen=120)
        self.error = '正在连接设备'
        self.stopping = threading.Event()
        self.remote = self.decoder = self.video = None

    def connect_socket(self, port, dummy=False):
        deadline = time.monotonic() + 12
        while not self.stopping.is_set():
            connection = None
            try:
                connection = socket.create_connection(('127.0.0.1', port), timeout=1)
                connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                if dummy and read_exact(connection, 1) != b'\0':
                    raise ConnectionError('设备握手失败')
                connection.settimeout(None)
                return connection
            except OSError:
                if connection:
                    connection.close()
                if time.monotonic() >= deadline:
                    raise ConnectionError('无法连接 Android 画面服务')
                self.stopping.wait(.1)
        raise ConnectionError('预览已关闭')

    def run(self):
        while not self.stopping.is_set():
            forward = None
            try:
                adb('push', args.scrcpy_server, '/data/local/tmp/aeroledger-preview.jar')
                session = secrets.randbelow(0x7fffffff)
                forward = adb('forward', 'tcp:0', 'localabstract:scrcpy_%08x' % session).decode().strip()
                self.remote = subprocess.Popen([
                    args.adb, '-s', args.serial, 'shell',
                    'CLASSPATH=/data/local/tmp/aeroledger-preview.jar', 'app_process', '/',
                    'com.genymobile.scrcpy.Server', '3.3.3', 'scid=%08x' % session,
                    'tunnel_forward=true', 'audio=false', 'control=true',
                    'max_size=1280', 'max_fps=30', 'video_bit_rate=4000000',
                    'send_device_meta=false', 'send_frame_meta=false',
                    'clipboard_autosync=false', 'stay_awake=true',
                ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                self.video = self.connect_socket(int(forward), dummy=True)
                control = self.connect_socket(int(forward))
                codec, width, height = struct.unpack('>4sII', read_exact(self.video, 12))
                if codec != b'h264' or not (0 < width <= 8192 and 0 < height <= 8192):
                    control.close()
                    raise RuntimeError('设备画面格式无效')
                with self.control_lock:
                    self.control = control
                    self.dimensions = (width, height)
                threading.Thread(target=self.drain_control, args=(control,), daemon=True).start()
                self.decoder = subprocess.Popen([
                    args.ffmpeg, '-hide_banner', '-loglevel', 'error',
                    '-probesize', '32', '-analyzeduration', '0', '-flags', 'low_delay',
                    '-threads', '1', '-f', 'h264', '-i', 'pipe:0', '-an',
                    '-threads', '1', '-c:v', 'mjpeg', '-q:v', '5',
                    '-fps_mode', 'passthrough', '-f', 'image2pipe', '-flush_packets', '1', 'pipe:1',
                ], stdin=self.video, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)
                data = bytearray()
                while not self.stopping.is_set():
                    chunk = os.read(self.decoder.stdout.fileno(), 65536)
                    if not chunk:
                        raise ConnectionError('设备画面已断开，正在重新连接')
                    data.extend(chunk)
                    while True:
                        end = data.find(b'\xff\xd9')
                        if end < 0:
                            break
                        image = bytes(data[:end + 2])
                        del data[:end + 2]
                        if not image.startswith(b'\xff\xd8'):
                            continue
                        # JPEG dimensions also track screen rotation after connection.
                        sof = image.find(b'\xff\xc0')
                        if sof >= 0:
                            height, width = struct.unpack('>HH', image[sof + 5:sof + 9])
                            self.dimensions = (width, height)
                        with self.condition:
                            self.frame = image
                            self.frame_at = time.monotonic()
                            self.frames_at.append(self.frame_at)
                            self.sequence += 1
                            self.error = None
                            self.condition.notify_all()
                    if len(data) > 8 * 1024 * 1024:
                        raise RuntimeError('设备画面超过大小限制')
            except Exception as error:
                with self.condition:
                    self.error = str(error)
                    self.condition.notify_all()
            finally:
                self.disconnect()
                if forward:
                    try:
                        adb('forward', '--remove', 'tcp:' + forward)
                    except Exception:
                        pass
            self.stopping.wait(1)

    @staticmethod
    def drain_control(control):
        try:
            while control.recv(4096):
                pass
        except OSError:
            pass

    def disconnect(self):
        with self.control_lock:
            if self.control:
                try:
                    self.control.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                self.control.close()
            self.control = None
        if self.video:
            self.video.close()
            self.video = None
        for process in (self.decoder, self.remote):
            if process and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        self.decoder = self.remote = None

    def stop(self):
        self.stopping.set()
        self.disconnect()
        with self.condition:
            self.condition.notify_all()

    def send(self, message):
        with self.control_lock:
            if not self.control or self.error:
                raise ConnectionError('设备正在连接，请稍后重试')
            self.control.sendall(message)

    def position(self, x, y):
        x, y = float(x), float(y)
        if not math.isfinite(x) or not math.isfinite(y):
            raise ValueError('坐标无效')
        width, height = self.dimensions
        return round(max(0, min(1, x)) * (width - 1)), round(max(0, min(1, y)) * (height - 1)), width, height

    def key(self, code, meta=0):
        self.send(b''.join(struct.pack('>BBIII', 0, action, code, 0, meta) for action in (0, 1)))

    def action(self, request):
        action = request.get('action')
        if action == 'touch':
            kind = {'down': 0, 'up': 1, 'move': 2, 'cancel': 3}.get(request.get('phase'))
            if kind is None:
                raise ValueError('触摸操作无效')
            self.send(struct.pack('>BBQiiHHHII', 2, kind, 0xfffffffffffffffe,
                                  *self.position(request['x'], request['y']),
                                  0 if kind in (1, 3) else 0xffff, 0, 0))
        elif action == 'tap':
            self.action(dict(request, action='touch', phase='down'))
            self.action(dict(request, action='touch', phase='up'))
        elif action == 'scroll':
            delta = float(request['delta'])
            if not math.isfinite(delta):
                raise ValueError('滚动距离无效')
            delta = max(-8, min(8, delta))
            self.send(struct.pack('>BiiHHhhI', 3, *self.position(request['x'], request['y']),
                                  0, round(-delta * 2048), 0))
        elif action == 'key':
            code = {'back': 4, 'home': 3, 'delete': 67, 'enter': 66}.get(request.get('key'))
            if code is None:
                raise ValueError('不支持的按键')
            self.key(code)
        elif action == 'text':
            value = request.get('text', '')
            if not isinstance(value, str) or not 0 < len(value) <= 200:
                raise ValueError('请输入 1～200 个字符')
            if request.get('replace'):
                # Include physical modifier events for Android IMEs and Compose.
                self.send(struct.pack('>BBIII', 0, 0, 113, 0, 0x3000))
                self.key(29, 0x3000)
                self.send(struct.pack('>BBIII', 0, 1, 113, 0, 0))
                time.sleep(.03)
            raw = value.encode('utf-8')
            self.send(struct.pack('>BQBI', 9, 0, 1, len(raw)) + raw)
        elif action == 'launch':
            package = b'cn.edu.buaa.ledger'
            self.send(struct.pack('>BB', 16, len(package)) + package)
        else:
            raise ValueError('不支持的操作')


device = Device()


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def setup(self):
        super().setup()
        self.connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

    def log_message(self, *_):
        pass

    def reply(self, code, body, kind='application/json; charset=utf-8'):
        self.send_response(code)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def stream(self):
        self.send_response(200)
        self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=frame')
        self.send_header('Cache-Control', 'no-store, no-transform')
        self.send_header('X-Accel-Buffering', 'no')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.close_connection = True
        self.connection.settimeout(3)
        self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 65536)
        previous = -1
        while not device.stopping.is_set():
            with device.condition:
                device.condition.wait_for(lambda: device.sequence != previous or device.stopping.is_set(), timeout=2)
                if not device.frame:
                    continue
                image, previous = device.frame, device.sequence
            # Read the latest frame; no queue of stale frames for a slow client.
            header = ('--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %d\r\n\r\n' % len(image)).encode()
            try:
                self.wfile.write(header + image + b'\r\n')
                self.wfile.flush()
            except (OSError, TimeoutError):
                return

    def do_GET(self):
        path = self.path.split('?', 1)[0]
        if path == '/':
            self.reply(200, html_file.read_text().replace('__PREVIEW_TOKEN__', token).encode(), 'text/html; charset=utf-8')
        elif path == '/stream.mjpeg':
            self.stream()
        elif path == '/screen.jpg':
            self.reply(200 if device.frame else 503, device.frame, 'image/jpeg')
        elif path == '/health':
            with device.condition:
                fps = sum(t > time.monotonic() - 1 for t in device.frames_at)
                data = {'ready': bool(device.frame) and device.error is None, 'version': 3, 'session': token[:8],
                        'pid': os.getpid(), 'serial': args.serial,
                        'width': device.dimensions[0], 'height': device.dimensions[1],
                        'fps': fps, 'frames': device.sequence, 'error': device.error,
                        'frame_age_ms': round((time.monotonic() - device.frame_at) * 1000) if device.frame else None}
            self.reply(200, json.dumps(data, ensure_ascii=False).encode())
        else:
            self.reply(404, b'{}')

    def do_POST(self):
        if self.path not in ('/action','/shutdown') or self.headers.get('X-Preview-Key') != token:
            self.close_connection = True
            self.reply(403, b'{}')
            return
        started = time.monotonic()
        try:
            length = int(self.headers.get('Content-Length', 0))
            if not 0 < length <= 4096:
                self.close_connection = True
                raise ValueError('请求大小无效')
            request=json.loads(self.rfile.read(length))
            if self.path=='/shutdown':
                self.reply(200,b'{"ok":true}')
                threading.Thread(target=shutdown,daemon=True).start()
                return
            device.action(request)
            self.reply(200, json.dumps({'ok': True, 'input_ms': round((time.monotonic() - started) * 1000, 2)}).encode())
        except Exception as error:
            self.reply(400, json.dumps({'error': str(error)}, ensure_ascii=False).encode())


server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
server.daemon_threads = True
threading.Thread(target=device.run, daemon=True).start()


def shutdown(*_):
    device.stop()
    threading.Thread(target=server.shutdown, daemon=True).start()


signal.signal(signal.SIGTERM, shutdown)
signal.signal(signal.SIGINT, shutdown)
print('Interactive Android preview v2: http://127.0.0.1:%d' % args.port, flush=True)
try:
    server.serve_forever()
finally:
    device.stop()
    server.server_close()

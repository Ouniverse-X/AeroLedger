# Interactive Browser Preview

网页版显示并控制真实 Android 应用。设备中的账目操作会实际保存；多人连接同一设备时操作同一份数据。

## Requirements

Python 3.10+、Android SDK Platform Tools（ADB）、FFmpeg，以及已启动的 Android 模拟器或已授权 USB 调试的测试手机。首次启动从 scrcpy 官方 GitHub 下载固定的 3.3.3 服务，校验 SHA-256 后缓存在系统临时目录。

推荐先在 Android Studio 启动设备，再运行：

```bash
./scripts/start-preview.sh
```

Windows 或不使用 Bash 时：

```text
python scripts/start-preview.py
```

打开 http://127.0.0.1:18880 。远程开发环境需转发端口 18880；服务只监听本机回环地址。已有页面可用 Ctrl+F5 刷新。

## Select a Device or APK

| Environment variable | Meaning |
| --- | --- |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | SDK 根目录；也可从本机 `local.properties` 读取 |
| `ANDROID_SERIAL` | `adb devices` 中的设备标识；多设备时应明确指定 |
| `LEDGER_PREVIEW_APK` | 指定安装包，默认 `releases/AeroLedger-v1.1.apk` |
| `LEDGER_PREVIEW_PORT` | 默认 `18880` |
| `LEDGER_AVD` | 无运行设备时，选择本机已创建的 AVD |
| `FFMPEG` | FFmpeg 可执行文件路径 |

启动器安装并打开 APK。签名不匹配时会停止并提示迁移，不会自动卸载已有账本。固定签名版设备可将 `LEDGER_PREVIEW_APK` 指向相同签名的安装包。

本机具备可用 AVD 时可启动无窗口模拟器；当前 Linux 开发机还包含 Docker / KVM 兼容路径。其他环境直接使用 Android Studio 已启动的设备即可，无需 Docker。

## Interaction

- 点击、按住拖动和鼠标滚轮直接控制设备。
- 先点击设备输入框，再用右侧输入区输入金额、英文或中文；可替换当前内容。
- 返回、系统桌面和打开应用按钮提供系统导航。
- 后台浏览器标签页暂停画面传输，恢复可见后重新连接。

视频由持续捕获、FFmpeg 解码与 MJPEG 展示组成，输入复用控制连接，移动和滚轮事件合并以减少积压。最长边 1280 像素，捕获最高 30 FPS，静止画面减少更新。

早期本机 Chromium 对比中，可见反馈中位数从 1055 ms 降至 167.5 ms，包含应用动画时间；远程网络、编码和设备负载会影响结果。该数据不代表所有连接环境。

## Stop

```bash
./scripts/stop-preview.sh
# Or: python scripts/stop-preview.py
```

停止本项目的预览服务和启动器创建的模拟器，不卸载应用。临时目录 `buaa-ledger-preview/` 中保存日志和服务 PID。参考服务来自 [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy)，协议客户端固定匹配 3.3.3。

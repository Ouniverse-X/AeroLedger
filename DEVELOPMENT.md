# Development Guide

本仓库包含完整、可构建的 Android 应用源码。文件与目录采用英文命名；应用界面与使用说明以中文为主。

## Requirements

- Android Studio，或 JDK 17 与 Android SDK 命令行工具。
- Android SDK Platform 35、Build Tools 35.0.0、Platform Tools。
- 首次构建需要联网获取 Gradle 与 Maven 依赖；应用运行无需网络。
- 最低运行版本 Android 8.0 / API 26；通知权限测试使用 Android 13 或以上设备。

## Open in Android Studio

1. 克隆本仓库，用 Android Studio 打开项目根目录。
2. 将 Gradle JDK 设为 JDK 17，等待 Gradle Sync 完成。
3. 在 SDK Manager 中安装 SDK 35 和 Build Tools 35.0.0。
4. 启动 Android 模拟器，或连接开启 USB 调试的安卓手机。
5. 选择 `app` 运行配置，点击 Run。

Android Studio 会生成本机 `local.properties`。命令行环境也可参考 `local.properties.example` 配置 `sdk.dir`；该文件不应提交。

## Build

```bash
git clone https://github.com/Ouniverse-X/AeroLedger.git
cd AeroLedger
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。安装包输出：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

仓库中的 Gradle Wrapper 固定为 Gradle 8.9，版本及分发包校验值均已提交。依赖固定为 AGP 8.7.3、Kotlin 1.9.24、Compose BOM 2024.06.00 和 Room 2.6.1。

Linux / Bash 环境还可以运行 `./scripts/build.sh`；它包含当前开发机旧 zlib 的局部兼容处理。正常环境直接使用 Gradle Wrapper 即可。

release 构建使用开发机的 debug 签名，适合课程演示。为团队后续版本配置独立发布签名并安全保管密钥；更新既有安装需要匹配原安装签名，或先导出数据再卸载重装。签名文件、密码、本机 SDK 路径均不入库。

## Test

无需 Android SDK 的业务检查：

```bash
./scripts/test-core.sh
```

构建与静态检查：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

在专用 Android 13+ 测试设备运行实际 Room、通知与界面测试：

```bash
ADB=/path/to/android-sdk/platform-tools/adb \
ANDROID_SERIAL=emulator-5554 ./scripts/test-device.sh
```

脚本会安装 debug 主包和测试包，并允许通知与精确提醒权限。使用专用模拟器；不要用存有个人账目的设备运行自动化测试。测试日志写入本地 `artifacts/tests/`，该目录不提交。

## Modify the Application

- 页面和交互：`app/src/main/java/cn/edu/buaa/ledger/ui/`。
- 颜色、字体、浅深色主题：`ui/theme/LedgerTheme.kt`。
- 分类、Room 表结构、DAO：`data/LedgerDatabase.kt`。
- 业务、导入和迁移：`data/LedgerRepository.kt`。
- 金额规则、CSV：`LedgerRules.java`、`CsvCodec.java`。
- 校园文本提取：`domain/CampusParser.java`。
- 系统提醒与接收器：`reminder/PaymentReminders.kt`。

修改数据库结构时，增加 Room 版本、编写迁移并更新 `app/schemas/`，不要使用破坏性清库代替迁移。修改金额、汇总或导入逻辑后运行相应业务与设备测试。

Compose Preview 基线位于 `ui/DesignPreviews.kt`。产品结构和业务边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## Scope of This Repository

上传内容包括应用、资源、数据库结构、测试、构建配置、英文命名的开发文档以及可安装 APK。参考仓库副本、开发缓存、课程文档、录屏和本机配置不在仓库中；上游参考与固定提交见 [REFERENCES.md](REFERENCES.md)。

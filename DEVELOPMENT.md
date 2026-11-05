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
./gradlew :app:assembleDemo
```

Windows 使用 `gradlew.bat`。安装包输出：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/demo/app-demo.apk`
- 固定签名构建：先配置 [RELEASE.md](RELEASE.md)，再运行 `./gradlew :app:assembleRelease`。

仓库中的 Gradle Wrapper 固定为 Gradle 8.9，版本及分发包校验值均已提交。依赖固定为 AGP 8.7.3、Kotlin 1.9.24、Compose BOM 2024.06.00 和 Room 2.6.1。

Linux / Bash 环境还可以运行 `./scripts/build.sh`；它包含当前开发机旧 zlib 的局部兼容处理。正常环境直接使用 Gradle Wrapper 即可。

`demo` 用于课程兼容安装，`release` 使用固定发布签名并拒绝缺少签名配置的构建。仓库已提供两类安装包，签名不同的首次迁移按完整备份流程完成。签名文件、密码、本机 SDK 路径均不入库。详见 [RELEASE.md](RELEASE.md)。

## Test

无需 Android SDK 的业务检查：

```bash
./scripts/test-core.sh
```

构建与静态检查：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

在专用 Android 8+ 测试设备运行实际 Room、通知与界面测试：

```bash
ADB=/path/to/android-sdk/platform-tools/adb \
ANDROID_SERIAL=emulator-5554 ./scripts/test-device.sh
```

脚本会安装 debug 主包和测试包，并按系统版本设置通知与精确提醒授权。使用专用模拟器；不要用存有个人账目的设备运行自动化测试。测试日志写入本地 `artifacts/tests/`，该目录不提交。

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

上传内容包括应用、资源、数据库结构、测试、构建配置、英文命名的开发文档以及可安装 APK。低延迟网页预览、生命周期检查与签名初始化脚本已提供，见 [PREVIEW.md](PREVIEW.md)、[DEVICE_TESTING.md](DEVICE_TESTING.md)。参考仓库副本、开发缓存、课程文档、录屏和本机配置不在仓库中；上游参考与固定提交见 [REFERENCES.md](REFERENCES.md)。

## New Modules

- `data/LedgerBackup.kt`：版本化完整备份、字段校验、冲突预览和事务恢复。
- `data/DraftStore.kt`：表单草稿与原子传输文件缓存。
- `ui/FormDrafts.kt`、`ui/BackupSheets.kt`：草稿持久化和恢复界面。
- `LedgerRepository.window()`：范围查询、SQL 聚合、分页与最近五笔；完整导出使用独立查询。
- `.github/workflows/`：固定 SHA 的自动检查、APK 检查与签名发布。

调整筛选时同步检查列表、分类图、趋势和筛选导出。预算及消费回顾保持全月全部类别，备注搜索不改变月度预算口径。新备份字段需升级文件格式或维持明确兼容策略；数据库 schema 本版仍为 Room v1，1.0 → 1.1 不清库。

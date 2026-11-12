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


## Receipt Recognition Development

保持现有单模块结构和数据库版本。`domain/EntryDraft` 表示可编辑提案；`data/EntryDrafts` 统一手动、文本、缴费和识图预填；`domain/CategoryCatalog` 是类别代码与显示名称的共同来源。新增手动字段时，应同步更新草稿与共用 `EntryEditor`。

`recognition/ReceiptImageReader` 只读图并限制资源，`OcrEngine` 只返回文字和坐标，`ReceiptParser` 只生成草稿。`RecognitionViewModel` 管理识别/编辑/已保存状态，`ReceiptActivity` 处理系统选择与分享。只有用户确认时才调用原有 `LedgerRepository.saveEntry`；勿在 OCR、解析器或分享回调中直接入账。

运行 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`，并继续运行 `scripts/test-core.sh` 的 48 项核心断言。设备端测试类新增 `ReceiptDeviceTests` 与 `ReceiptUiTests`，使用动态生成的虚构图片，测试结束清理自身文件和账目；不要清空开发设备数据。

离线验收在专用API35模拟器开启飞行模式并关闭Wi-Fi后执行。提醒测试需通知和精确闹钟权限，UI测试需唤醒并解锁模拟器。`ReceiptUiTests`同时覆盖真实Photo Picker、模拟选择结果、分享冷/热启动、候选勾选与重建。`CaptureDeviceTests`通过系统磁贴及真实授权对话框捕获测试APK中的虚构页面，测试APK不包含真实账单。测试会向快捷设置添加磁贴，结束后可自行移除。

### Responsibilities and extension points

- `ReceiptLayout`负责同一行文字组合和明显订单边界，`ReceiptParser`负责字段角色、日期政策、状态提示和现有类别建议；不按平台写独立模板，不按金额去重。
- `RecognitionState`持有候选列表、选择/保存状态和当前草稿ID；`RecognitionViewModel`只对选中且尚未保存的当前草稿调用原Repository。每次保存完成后进入下一笔，返回列表不丢用户修改；没有跨笔原子批量事务。
- `capture/`仅负责授权、前台服务、单帧与结果缓存。每次会话使用新授权，单个VirtualDisplay通过resize适应窗口变化；有界保留最新帧以适应静态页面。完成、取消、失败、系统停止均释放Image/Reader/Display/Projection。API34以下旋转中止并提示重试。
- 新增手动字段时同步修改EntryDraft、EntryDrafts、共用EntryEditor及其校验；图片无法可靠推断的字段保持未知，让同一编辑器补齐。类别继续使用CategoryCatalog，不复制另一套校验表。

截图实现依据：[MediaProjection](https://developer.android.com/media/grow/media-projection)、[Quick Settings](https://developer.android.com/develop/ui/views/quicksettings-tiles)。不要关闭系统截图保护或请求无障碍服务来绕过不可捕获窗口。

### Local data regression

`ReceiptFixturesTests`在调试应用私有`files/fictional-samples`生成20张虚构PNG，覆盖七类来源标签和缺字段、冲突、同额多单等场景。它们验证协议与流程，不模拟真实平台全部视觉布局。

`ReceiptCorpusTests`默认跳过，仅在显式传入`-e privateCorpus true`时读取调试应用私有`files/corpus/*.png`并输出`ocr-results.json`；不写账。将获授权的图片通过ADB临时目录与`run-as`复制到该目录，再执行：

```text
adb shell am instrument -w -e class cn.edu.buaa.ledger.ReceiptCorpusTests -e privateCorpus true cn.edu.buaa.ledger.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as cn.edu.buaa.ledger cat files/corpus/ocr-results.json
```

将输出重定向到本地被忽略目录，勿粘贴完整OCR到公共日志。评测须逐图记录漏单、错金额、错日期和状态，人工标注与OCR输出分开。开发集不能当作盲测；同单不同截图不算独立交易证据。真实截图、生成PNG、OCR结果和本机计划均不进Git。团队可复用的测试源码正常提交。

### Receipt correctness regression (v1.0.1)

新增 JVM 回归覆盖失败/取消付款、分行下单/发货时间以及首尾状态两种多单布局。`ReceiptUiTests.originalPreviewAndReplacementProtectUnsavedEdits` 覆盖原图打开与 Activity 重建、取消替换、坏图替换失败保留以及成功后清理旧原图；需连接专用设备执行，编译测试包不代表设备执行通过。

原图副本仅保留在私有 `cache/receipt-sessions`，`RecognitionState` 只存文件名；预览复用有界解码与 EXIF 转正。正常会话退出和成功替换会删除当前旧图；异常退出留下的副本，在后续进入时清理一小时前的非当前文件。用户确认替换后依然保留旧会话，待新图成功解析后才切换。

# Device Verification

业务检查：`./scripts/test-core.sh`。构建与 lint：`./scripts/build.sh`。测试应使用专用设备，自动化会创建临时账目、调整通知授权和模拟应用进程退出。

## Instrumentation

```bash
ADB=/path/to/android-sdk/platform-tools/adb \
ANDROID_SERIAL=emulator-5554 ./scripts/test-device.sh
```

脚本按 Android 版本设置通知与精确提醒授权，安装主包和测试包，并检查 instrumentation 返回结果。覆盖 Room、历史迁移、预算、CSV、完整备份策略、SQL 分页与搜索、持久草稿、真实通知、图表边界及界面新增 / 修改 / 删除撤销。

## Process Recreation and Upgrade

需要带标准库 SQLite 支持的 Python 3，命令中的标识明确表示专用测试设备：

```bash
ANDROID_SERIAL=emulator-5554 python3 scripts/test-lifecycle.py --allow-test-device
```

脚本在退到桌面后终止该调试应用自己的进程，重新打开并核对草稿，再验证保留、恢复和放弃草稿。对正式数据库各表的内容摘要进行前后比较，确认未保存草稿没有变成账目。

在可清空的专用设备验证真实升级：

```bash
python3 scripts/test-lifecycle.py --allow-test-device --serial emulator-5554 \
  --reset --old-apk releases/AeroLedger-v1.0.apk \
  --apk app/build/outputs/apk/debug/app-debug.apk
```

`--reset` 会卸载该设备上的航小账，只能在专用测试设备使用。新旧 APK 必须采用相同证书；此检查使用兼容证书的开发构建。流程加载旧版 20 笔示例，再升级、核对预算与金额，并检查草稿生命周期。

## Hardware Checklist

当前开发环境仅连接模拟器。真实手机、厂商节电策略和 TalkBack 手势需要由持有设备的成员按以下步骤复核，不能用模拟器结果代替：

1. 在手机安装课程版，新增并改删一笔账目，确认预算、筛选、图表、搜索和撤销一致。
2. 创建短期缴费计划，允许通知，退到桌面并锁屏，确认通知到达；已缴、取消或关闭后撤销，账目不自动新增。
3. 在设备省电模式下重复提醒流程，记录通知和精确提醒授权、到达时间及厂商后台限制设置。
4. 保存待缴计划后重启，确认恢复；系统“强行停止”后须重新打开应用，不能据此承诺后台送达。
5. 编辑草稿后正常切后台、旋转、由系统回收进程再打开；已保存账目与未保存草稿分别核对。
6. 用系统文件选择器导出完整备份，在另一设备恢复，核对账目、预算、计划、回顾、主题和冲突策略。
7. 检查 320 dp、小屏、横屏、1.5 倍字体、浅深色；开启 TalkBack，核对分类选中状态、金额和月份操作。

## Evidence

本地日志在 `artifacts/tests/`，CI 日志和报告在 GitHub Actions 产物中。具体已执行范围与结果随版本记录，失败项修复后重新运行，未接入的真实硬件不记为已验证。

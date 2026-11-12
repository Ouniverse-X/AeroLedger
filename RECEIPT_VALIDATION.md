# Receipt recognition v1.0.1 validation

本版恢复离线图片记账，并修复字段误判、漏单和会话替换问题。版本为 1.0.1 / versionCode 2；基于远端 v1.0，未整合独立的本地 v1.2.7。

## Corrected behavior

| 输入或操作 | 本版行为 |
| --- | --- |
| “交易失败 / 支付金额 ￥25.00 / 交易时间 2026-11-12” | 金额、日期留空，提示核对状态，不默认选中 |
| “已取消 / 实付款 ￥25.00 / 付款时间 2026-11-12” | 不作为成功付款候选预填 |
| “实付 ￥25.00 / 下单时间 / 2026-11-10” | 支付日期留空，不借用下单日期；发货、签收等日期同样处理 |
| 两张订单卡的状态均位于卡片末尾 | 两笔金额、日期及商户均保留，不丢第一笔，不制造末尾空候选 |
| 核对时点击“查看原图” | 打开当前会话私有原图，可双指缩放/拖动；关闭后保留已改字段 |
| 未保存候选返回列表后导入新图 | 先确认替换；取消保留旧会话，识别失败同样保留，成功后才替换并删除旧原图 |
| 授权说明弹窗旋转重建 | 说明重新显示；已进入系统授权流程时不重复发起授权 |

多候选仍需逐笔人工核对后保存。退款、外币、复杂版式和跨图片订单去重沿用 README 中的明确限制。

## Automated checks

运行命令：

```bash
./scripts/test-core.sh
./scripts/build.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --max-workers=4
```

- 核心 Java 业务规则：48 项断言通过。
- JVM 解析与场景测试：17 项通过，0 失败、0 跳过。覆盖失败/取消状态、分行非支付日期、状态首尾布局，以及原有字段优先级、同额多单和手动/文本录入兼容性。
- 原审查的失败交易、取消交易、分行下单/发货日期、末尾状态双订单均以原解析器运行独立复现程序，修复后结果与表格一致；正常付款对照保持正确。
- 新增设备 UI 回归 `ReceiptUiTests.originalPreviewAndReplacementProtectUnsavedEdits`，覆盖原图及旋转、取消替换、坏图失败保留、成功替换与旧图清理。设备测试包仅编译，未执行。

最终 debug/release APK 与设备测试 APK 均构建通过。debug/release lint 均为 0 错误；各有 18 条依赖可升级提示，未在本轮更换既有固定依赖。APK 核对为 1.0.1 / versionCode 2、minSdk 26，无 INTERNET 权限；签名 SHA-256 与原 v1.0 一致。`releases/SHA256SUMS` 包含新旧安装包校验值。

## Physical-device limits

验证时 ADB 没有在线设备，未启动模拟器。没有将编译通过计为设备交互通过。仍需在专用设备验收：真实相册与外部分享、原图缩放和旋转、坏图替换、通知拒绝、截屏授权/取消/旋转，以及无 GMS 手机上的离线 OCR。真实平台截图准确率未做盲测，不以虚构来源标签样例声称平台准确率。

## Package compatibility

`releases/AeroLedger-v1.0.1.apk` 沿用原 v1.0 课程 APK 的 debug 签名，数据库版本与类别代码不变。它不是本地 v1.2.7 的覆盖升级包。更新前导出 CSV，历史 v1.0 APK 继续保留。

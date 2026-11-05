# Architecture

AeroLedger（航小账）采用 Kotlin、Jetpack Compose Material 3、Room、协程与 MVVM。数据保存在本机，无后端和银行或支付账户连接。

## Structure

```text
app/src/main/java/cn/edu/buaa/ledger/
├── MainActivity.kt           Android 生命周期、系统栏与通知入口
├── LedgerApp.kt              应用级数据仓库与初始化
├── data/
│   ├── LedgerDatabase.kt     Room 实体、DAO、稳定类别
│   ├── LedgerRepository.kt   校验、事务、导入、演示数据、原型迁移
│   └── AppearanceStore.kt    DataStore 主题设置
├── domain/
│   ├── LedgerModels.kt       筛选、统计、导入预览与回顾快照
│   └── CampusParser.java     本地文本提取和分类规则
├── reminder/
│   └── PaymentReminders.kt   闹钟、通知、接收与重启恢复
└── ui/
    ├── LedgerViewModel.kt    可观察状态与业务操作
    ├── LedgerRoot.kt         导航、权限、文件流程与编辑面板
    ├── Screens.kt            总览、账目、统计、缴费
    ├── Editors.kt            收支、预算、计划和消费回顾编辑
    ├── DesignPreviews.kt     Compose Preview
    ├── components/           共享组件与交互图表
    └── theme/                设计令牌与浅深色主题
```

## Data

Room 数据库为 `campus-ledger-v2.db`，结构快照位于 `app/schemas/`。主要表为 `categories`、`transactions`、`monthly_budgets`、`payment_plans`、`monthly_reviews`、`metadata`、`import_rows`。

金额使用整数分保存，输入由 BigDecimal 精确转换；收支类型独立保存。账目日期采用自然日 epochDay；缴费到期时间采用绝对毫秒时间，同时保留时区。DAO 返回 Flow，账目变更后刷新页面统计。

FilterSpec 统一明细、分类图与筛选导出的条件。预算固定使用指定月全部支出，收入不抵扣预算。趋势图显示选定月及此前五个月，并使用同样的类型与类别筛选。消费回顾保存正文和生成依据，统计变化后保留编辑稿并提示重新生成。

## Reminders

缴费计划与正式账目分离，标记已缴不会自动入账。计划具有待缴、已缴、取消状态，单项提醒开关、提醒版本和已通知版本。

修改到期时间、状态或开关会推进提醒版本并替换旧闹钟；接收广播时重新检查版本、状态、权限、到期时间与是否已发送。计划修改、恢复和通知发送由同一个 Mutex 串行组织，撤销同时移除闹钟和已显示通知。

已授权时使用精确闹钟，否则使用系统弹性调度。设备重启、时间变化及应用回到前台后恢复有效计划；强行停止后需要重新打开应用。

## Import and Text Assistance

CSV 先完整解析与预览，确认后在事务内写入。相同记录 ID 或同一文件已导入行属于确定重复；完整字段相同但 ID 不同属于疑似重复，由用户决定保留。格式损坏不写入，字段无效逐条反馈。

文本辅助录入基于本地规则，显示原文、候选金额、日期和分类依据。歧义及缺失字段由用户核对；确认前不生成正式账目。

## Offline Behavior

应用不申请网络权限，不提供账号和跨设备同步。每个成员拥有独立本地数据；CSV 可以手动导入导出账目。通知、精确提醒及开机权限服务于本地缴费提醒，系统文件选择器提供文件授权。

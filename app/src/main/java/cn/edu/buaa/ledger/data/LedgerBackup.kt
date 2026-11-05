package cn.edu.buaa.ledger.data

import androidx.room.withTransaction
import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.reminder.PaymentReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class LedgerBackup(val entries: List<TransactionEntity>, val budgets: List<BudgetEntity>,
    val plans: List<PaymentPlan>, val reviews: List<ReviewEntity>, val metadata: List<MetaEntity>,
    val receipts: List<ImportReceipt>, val theme: String, val createdAt: Long)
enum class RestoreMode(val label: String) {
    KEEP_LOCAL("合并 · 冲突保留本机"), USE_BACKUP("合并 · 冲突采用备份"), REPLACE_ALL("完整替换本机账本")
}
data class RestorePreview(val backup: LedgerBackup, val filename: String, val conflicts: Int,
    val identical: Int, val newRecords: Int, val currentRecords: Int, val differences:List<String> = emptyList())

/** Versioned logical backup. Validate the entire document before opening a write transaction. */
object BackupCodec {
    const val MAX_BYTES = 20_000_000
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun JSONObject.long(key: String): Long = get(key).toString().toLongOrNull()
        ?: throw IllegalArgumentException("备份字段 $key 不是整数")
    private fun JSONObject.text(key: String, max: Int): String {
        val value = get(key); require(value is String && value.length <= max) { "备份字段 $key 无效" }; return value
    }
    private fun <T> JSONArray.items(read: (JSONObject) -> T): List<T> {
        require(length() <= 50000) { "备份记录超过 50000 条限制" }
        return (0 until length()).map { read(getJSONObject(it)) }
    }
    private fun <T> array(values: List<T>, write: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(write(it)) } }
    fun entry(item: TransactionEntity) = JSONObject().put("id",item.id).put("type",item.type)
        .put("amountCents",item.amountCents).put("dateEpochDay",item.dateEpochDay).put("categoryCode",item.categoryCode)
        .put("note",item.note).put("sourceType",item.sourceType).put("sourceText",item.sourceText)
        .put("createdAt",item.createdAt).put("updatedAt",item.updatedAt)
    fun entry(value: JSONObject): TransactionEntity = TransactionEntity(value.text("id",80),value.text("type",16),
        value.long("amountCents"),value.long("dateEpochDay"),value.text("categoryCode",40),value.text("note",200),
        value.text("sourceType",40),value.text("sourceText",10000),value.long("createdAt"),value.long("updatedAt")).also {
        LedgerRules.validate(it.toEntry()); require(it.createdAt >= 0 && it.updatedAt >= 0) { "账目时间无效" }
    }
    private fun plan(p: PaymentPlan) = JSONObject().put("id",p.id).put("name",p.name).put("amountCents",p.amountCents)
        .put("dueAt",p.dueAt).put("zoneId",p.zoneId).put("status",p.status).put("reminderEnabled",p.reminderEnabled)
        .put("revision",p.revision).put("notifiedRevision",p.notifiedRevision)
    private fun review(p: ReviewEntity) = JSONObject().put("month",p.month).put("body",p.body)
        .put("statisticsSnapshot",p.statisticsSnapshot).put("generatedAt",p.generatedAt).put("savedAt",p.savedAt)
    fun encode(backup: LedgerBackup): String {
        val data = JSONObject().put("entries",array(backup.entries,::entry))
            .put("budgets",array(backup.budgets) { JSONObject().put("month",it.month).put("amountCents",it.amountCents) })
            .put("plans",array(backup.plans,::plan)).put("reviews",array(backup.reviews,::review))
            .put("metadata",array(backup.metadata) { JSONObject().put("key",it.key).put("value",it.value) })
            .put("receipts",array(backup.receipts) { JSONObject().put("fileHash",it.fileHash).put("ordinal",it.ordinal).put("transactionId",it.transactionId) })
            .put("theme",backup.theme).toString()
        return JSONObject().put("format","AeroLedgerBackup").put("version",1).put("createdAt",backup.createdAt)
            .put("sha256",sha(data)).put("data",data).toString(2).also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "账本超过 20 MB 备份上限" }
            }
    }
    fun decode(text: String): LedgerBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件超过 20 MB" }
        val root = JSONObject(text)
        require(root.getString("format") == "AeroLedgerBackup" && root.long("version") == 1L) { "不是受支持的 AeroLedger 备份" }
        val raw = root.getString("data")
        require(sha(raw) == root.getString("sha256")) { "备份校验失败，文件可能不完整或已修改" }
        val data = JSONObject(raw)
        val entries = data.getJSONArray("entries").items(::entry)
        val budgets = data.getJSONArray("budgets").items {
            val month = it.text("month",7); val cents = it.long("amountCents")
            require(YearMonth.parse(month).year in 1900..2100 && cents in 1..LedgerRules.MAX_CENTS) { "预算数据无效" }
            BudgetEntity(month,cents)
        }
        val plans = data.getJSONArray("plans").items {
            require(it.get("reminderEnabled") is Boolean) { "提醒开关无效" }
            PaymentPlan(it.long("id"),it.text("name",40),it.long("amountCents"),it.long("dueAt"),it.text("zoneId",80),
                it.text("status",16),it.getBoolean("reminderEnabled"),it.long("revision"),it.long("notifiedRevision")).also { p ->
                require(p.id > 0 && p.name.isNotBlank() && p.amountCents in 1..LedgerRules.MAX_CENTS &&
                    p.status in listOf("pending","paid","cancelled") && p.revision in 1 until Long.MAX_VALUE &&
                    p.notifiedRevision in 0..p.revision) { "缴费计划无效" }
                ZoneId.of(p.zoneId)
                require(java.time.Instant.ofEpochMilli(p.dueAt).atZone(ZoneId.of(p.zoneId)).year in 1900..2100) { "计划日期无效" }
            }
        }
        val reviews = data.getJSONArray("reviews").items {
            ReviewEntity(it.text("month",7),it.text("body",10000),it.text("statisticsSnapshot",MAX_BYTES),
                it.long("generatedAt"),it.long("savedAt")).also { p ->
                require(YearMonth.parse(p.month).year in 1900..2100 && p.generatedAt >= 0 && p.savedAt >= 0) { "回顾数据无效" }
            }
        }
        val metadata = data.getJSONArray("metadata").items {
            MetaEntity(it.text("key",80),it.text("value",10000)).also { p ->
                require(p.key in setOf("demo_loaded","prototype_migrated")) { "备份包含未知设置" }
            }
        }
        val receipts = data.getJSONArray("receipts").items {
            val ordinal = it.long("ordinal")
            ImportReceipt(it.text("fileHash",64),ordinal.toInt(),it.text("transactionId",80)).also { p ->
                require(p.fileHash.matches(Regex("[0-9a-f]{64}")) && ordinal in 2..10001 &&
                    p.transactionId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "导入记录无效" }
            }
        }
        fun unique(keys: List<Any>) { require(keys.toSet().size == keys.size) { "备份含重复记录标识" } }
        unique(entries.map { it.id }); unique(budgets.map { it.month }); unique(plans.map { it.id })
        unique(reviews.map { it.month }); unique(metadata.map { it.key }); unique(receipts.map { it.fileHash to it.ordinal })
        val theme = data.text("theme",10); require(theme in listOf("system","light","dark")) { "外观设置无效" }
        val created = root.long("createdAt"); require(created >= 0) { "备份时间无效" }
        return LedgerBackup(entries,budgets,plans,reviews,metadata,receipts,theme,created)
    }
}

suspend fun LedgerRepository.createBackup(theme: String): LedgerBackup = database.withTransaction {
    LedgerBackup(dao.transactions(),dao.observeBudgets().first(),dao.plans(),dao.observeReviews().first(),
        dao.metadata().filter { it.key in setOf("demo_loaded","prototype_migrated") },dao.allReceipts(),theme,System.currentTimeMillis())
}

suspend fun LedgerRepository.previewBackup(text: String, filename: String): RestorePreview = withContext(Dispatchers.IO) {
    val backup = BackupCodec.decode(text)
    database.withTransaction {
        val local = createBackup("system")
        var conflicts = 0; var identical = 0; var added = 0
        val differences=mutableListOf<String>()
        fun <T,K> compare(current: List<T>, incoming: List<T>, label:String, describe:(T)->String, key: (T)->K) {
            val byKey = current.associateBy(key)
            for (value in incoming) {
                val old = byKey[key(value)]
                when { old == null -> added++; old == value -> identical++; else -> {
                    conflicts++
                    if(differences.size<30)differences.add("$label · ${key(value)}\n本机：${describe(old)}\n备份：${describe(value)}")
                } }
            }
        }
        compare(local.entries.map{it.copy(createdAt=0,updatedAt=0)},backup.entries.map{it.copy(createdAt=0,updatedAt=0)},"账目",
            {"${LocalDate.ofEpochDay(it.dateEpochDay)} · ¥${LedgerRules.money(it.amountCents)} · ${it.note.take(60)}"}) { it.id }
        compare(local.budgets,backup.budgets,"预算",{"¥${LedgerRules.money(it.amountCents)}"}) { it.month }
        compare(local.plans,backup.plans,"缴费计划",{"${it.name} · ¥${LedgerRules.money(it.amountCents)} · ${java.time.Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime()} · ${when(it.status){"paid"->"已缴";"cancelled"->"已取消";else->"待缴"}}"}) { it.id }
        compare(local.reviews,backup.reviews,"消费回顾",{it.body.take(100)}) { it.month }
        RestorePreview(backup,filename,conflicts,identical,added,
            local.entries.size+local.budgets.size+local.plans.size+local.reviews.size,differences)
    }
}

/** Rechecks conflicts inside the transaction, even if records changed after preview. */
suspend fun LedgerRepository.restoreBackup(backup: LedgerBackup, mode: RestoreMode): Int = paymentMutex.withLock {
    val oldPlans = dao.plans()
    val restored = database.withTransaction {
        val replace = mode == RestoreMode.REPLACE_ALL
        if (replace) {
            dao.clearReceipts(); dao.clearTransactions(); dao.clearBudgets(); dao.clearPlans(); dao.clearReviews(); dao.clearMetadata()
        }
        var count = 0
        val existingEntries = dao.transactions().associateBy { it.id }
        val existingBudgets = dao.observeBudgets().first().associateBy { it.month }
        val existingPlans = dao.plans().associateBy { it.id }
        val existingReviews = dao.observeReviews().first().associateBy { it.month }
        fun accept(exists: Boolean) = !exists || mode != RestoreMode.KEEP_LOCAL
        backup.entries.forEach { if (accept(it.id in existingEntries)) { dao.saveTransaction(it); count++ } }
        backup.budgets.forEach { if (accept(it.month in existingBudgets)) { dao.saveBudget(it); count++ } }
        backup.plans.forEach { if (accept(it.id in existingPlans)) {
            // A restored pending plan gets a fresh revision; old broadcasts cannot match it.
            val old = oldPlans.firstOrNull { plan -> plan.id==it.id }
            require(maxOf(old?.revision?:0,it.revision)<Long.MAX_VALUE-1){"计划版本超过允许范围"}
            val revision = maxOf(old?.revision ?: 0,it.revision)+1
            dao.restorePlan(it.copy(revision=revision,notifiedRevision=if(it.notifiedRevision==it.revision)revision else 0)); count++
        } }
        backup.reviews.forEach { if (accept(it.month in existingReviews)) { dao.saveReview(it); count++ } }
        backup.metadata.forEach { if (accept(dao.meta(it.key)!=null)) dao.setMeta(it) }
        dao.setMeta(MetaEntity("prototype_migrated","true"))
        val existingReceipts = dao.allReceipts().associateBy { it.fileHash to it.ordinal }
        backup.receipts.forEach { if (accept((it.fileHash to it.ordinal) in existingReceipts)) dao.restoreReceipt(it) }
        count
    }
    oldPlans.forEach { PaymentReminders.cancel(context,it.id) }
    // Still under the payment mutex: no old receiver can deliver during rescheduling.
    dao.plans().forEach { PaymentReminders.schedule(context,it) }
    restored
}

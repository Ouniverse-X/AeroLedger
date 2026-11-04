package cn.edu.buaa.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import cn.edu.buaa.ledger.CsvCodec
import cn.edu.buaa.ledger.Entry
import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.reminder.PaymentReminders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class LedgerRepository(val context: Context, val database: LedgerDatabase) {
    val dao = database.ledger()
    val paymentMutex = Mutex()
    val data: Flow<LedgerData> = combine(dao.observeTransactions(), dao.observeBudgets(), dao.observePlans(), dao.observeReviews()) {
        entries, budgets, plans, reviews -> LedgerData(entries, budgets, plans, reviews, true)
    }

    suspend fun initialize() {
        dao.seedCategories(campusCategories)
        migratePrototype()
    }

    suspend fun saveEntry(item: TransactionEntity) {
        LedgerRules.validate(item.toEntry())
        val old = dao.transaction(item.id)
        dao.saveTransaction(item.copy(createdAt = old?.createdAt ?: item.createdAt, updatedAt = System.currentTimeMillis()))
    }
    suspend fun setBudget(month: YearMonth, amount: Long) {
        require(amount in 1..LedgerRules.MAX_CENTS) { "预算金额超出范围" }
        dao.saveBudget(BudgetEntity(month.toString(), amount))
    }

    suspend fun savePlan(plan: PaymentPlan): Long = paymentMutex.withLock {
        require(plan.name.isNotBlank() && plan.name.length <= 40) { "项目名称需为 1～40 字" }
        require(plan.amountCents in 1..LedgerRules.MAX_CENTS) { "金额超出范围" }
        require(plan.status in listOf("pending", "paid", "cancelled"))
        val old = if (plan.id == 0L) null else dao.plan(plan.id)
        val changed = old == null || old.dueAt != plan.dueAt || old.reminderEnabled != plan.reminderEnabled || old.status != plan.status
        val next = plan.copy(name = plan.name.trim(), revision = if (changed) (old?.revision ?: 0) + 1 else old!!.revision,
            notifiedRevision = if (changed) 0 else old!!.notifiedRevision)
        val id = if (old == null) dao.insertPlan(next.copy(id = 0)) else { dao.updatePlan(next); next.id }
        PaymentReminders.schedule(context, next.copy(id = id))
        id
    }

    suspend fun previewCsv(text: String, filename: String): ImportPreview = database.withTransaction {
        val parsed = CsvCodec.parse(text)
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        val known = dao.transactions()
        val ids = known.map { it.id }.toMutableSet()
        val fingerprints = known.map { it.toEntry().fingerprint() }.toMutableSet()
        val receipts = dao.receipts(hash).map { it.ordinal }.toSet()
        val rows = parsed.drop(1).mapIndexed { index, fields ->
            val ordinal = index + 2
            try {
                val entry = CsvCodec.decodeRow(fields).toEntity("csv", filename)
                val state = when {
                    ordinal in receipts || entry.id in ids -> "duplicate"
                    entry.toEntry().fingerprint() in fingerprints -> "suspicious"
                    else -> "valid"
                }
                ids.add(entry.id); fingerprints.add(entry.toEntry().fingerprint())
                ImportRow(ordinal, entry, state, when(state) { "duplicate" -> "记录 ID 或文件记录已导入，不覆盖已有账目"; "suspicious" -> "字段相同但 ID 不同，请核对是否为另一笔真实消费"; else -> "校验通过" })
            } catch (e: IllegalArgumentException) { ImportRow(ordinal, null, "invalid", e.message ?: "格式错误", fields.joinToString(" · ")) }
        }
        ImportPreview(hash, filename, rows)
    }

    suspend fun commitImport(preview: ImportPreview, keepSuspicious: Boolean): ImportOutcome = database.withTransaction {
        var added = 0; var skipped = 0
        val receipts = dao.receipts(preview.hash).map { it.ordinal }.toMutableSet()
        for (row in preview.rows) {
            val entry = row.entry
            if (entry == null) continue
            if (row.state == "duplicate" || row.ordinal in receipts || dao.transaction(entry.id) != null ||
                row.state == "suspicious" && !keepSuspicious) { skipped++; continue }
            LedgerRules.validate(entry.toEntry())
            dao.saveTransaction(entry)
            dao.saveReceipt(ImportReceipt(preview.hash, row.ordinal, entry.id))
            receipts.add(row.ordinal); added++
        }
        ImportOutcome(added, skipped, preview.invalid)
    }

    suspend fun seedDemo(): Boolean = database.withTransaction {
        if (dao.meta("demo_loaded") != null) return@withTransaction false
        val month = YearMonth.now()
        val records = listOf(
            listOf("income", "2000.00", "生活费", "家人转入生活费"), listOf("expense", "12.50", "餐饮", "学一食堂午餐"),
            listOf("expense", "18.00", "餐饮", "合一楼晚餐"), listOf("expense", "36.00", "学习", "课程资料打印"),
            listOf("expense", "6.00", "交通", "地铁出行"), listOf("expense", "59.90", "购物", "宿舍日用品"),
            listOf("expense", "50.00", "生活缴费", "宿舍电费分摊"), listOf("income", "500.00", "兼职", "助教劳务"),
            listOf("expense", "15.00", "餐饮", "沙河校区午餐"), listOf("expense", "8.00", "其他支出", "校园明信片")
        )
        records.forEachIndexed { i, row ->
            val date = month.atDay(minOf(LocalDate.now().dayOfMonth, i + 1))
            dao.saveTransaction(Entry("demo-$month-$i", row[0], LedgerRules.parseMoney(row[1]), date.toString(), row[2], "${row[3]}（示例）").toEntity("demo"))
        }
        // Previous months provide an honest trend; every entry remains labelled as fictional.
        for (i in 1..5) {
            val m = month.minusMonths(i.toLong())
            val expense = if (i == 1) 3200L else listOf(0, 0, 18600, 24500, 12800, 32800)[i].toLong()
            dao.saveTransaction(Entry("demo-history-e-$i", "expense", expense, m.atDay(15).toString(), "餐饮", "历史食堂消费（示例）").toEntity("demo"))
            dao.saveTransaction(Entry("demo-history-i-$i", "income", 180000, m.atDay(1).toString(), "生活费", "历史生活费（示例）").toEntity("demo"))
        }
        if (dao.budget(month.toString()) == null) setBudget(month, 150000)
        dao.setMeta(MetaEntity("demo_loaded", "true"))
        true
    }

    private suspend fun migratePrototype() {
        if (dao.meta("prototype_migrated") != null) return
        val oldFile = context.getDatabasePath("campus-ledger.db")
        if (oldFile.exists()) withContext(Dispatchers.IO) {
            android.database.sqlite.SQLiteDatabase.openDatabase(oldFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { old ->
                database.withTransaction {
                    old.rawQuery("SELECT id,type,cents,date,category,note FROM entries", null).use { c ->
                        while (c.moveToNext()) {
                            val e = Entry(c.getString(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5))
                            LedgerRules.validate(e); if (dao.transaction(e.id) == null) dao.saveTransaction(e.toEntity("prototype"))
                        }
                    }
                    old.rawQuery("SELECT month,cents FROM budgets", null).use { c -> while(c.moveToNext()) dao.saveBudget(BudgetEntity(c.getString(0),c.getLong(1))) }
                    old.rawQuery("SELECT name,cents,due_at,status,enabled,notified FROM bills",null).use { c ->
                        while(c.moveToNext()) dao.insertPlan(PaymentPlan(name=c.getString(0),amountCents=c.getLong(1),dueAt=c.getLong(2),
                            status=c.getString(3),reminderEnabled=c.getInt(4)==1,notifiedRevision=if(c.getInt(5)==1) 1 else 0))
                    }
                    old.rawQuery("SELECT month,body FROM reviews",null).use { c -> while(c.moveToNext()) dao.saveReview(ReviewEntity(c.getString(0),c.getString(1),"prototype",0)) }
                    old.rawQuery("SELECT value FROM settings WHERE name='demo_loaded'",null).use { c -> if(c.moveToFirst()) dao.setMeta(MetaEntity("demo_loaded","true")) }
                    dao.setMeta(MetaEntity("prototype_migrated","true"))
                }
            }
        } else dao.setMeta(MetaEntity("prototype_migrated", "true"))
    }

    companion object {
        fun create(context: Context): LedgerRepository {
            val app = context.applicationContext
            val db = Room.databaseBuilder(app, LedgerDatabase::class.java, "campus-ledger-v2.db").build()
            return LedgerRepository(app, db)
        }
    }
}

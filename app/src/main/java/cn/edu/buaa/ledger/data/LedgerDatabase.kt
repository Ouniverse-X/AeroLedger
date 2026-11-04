package cn.edu.buaa.ledger.data

import androidx.room.*
import cn.edu.buaa.ledger.Entry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val code: String, val name: String, val type: String, val sortOrder: Int)

@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity = CategoryEntity::class,
    parentColumns = ["code"], childColumns = ["categoryCode"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("dateEpochDay"), Index("categoryCode")])
data class TransactionEntity(
    @PrimaryKey val id: String, val type: String, val amountCents: Long,
    val dateEpochDay: Long, val categoryCode: String, val note: String,
    val sourceType: String = "manual", val sourceText: String = "",
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "monthly_budgets")
data class BudgetEntity(@PrimaryKey val month: String, val amountCents: Long)

@Entity(tableName = "payment_plans")
data class PaymentPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val amountCents: Long,
    val dueAt: Long, val zoneId: String = java.time.ZoneId.systemDefault().id,
    val status: String = "pending", val reminderEnabled: Boolean = true,
    val revision: Long = 1, val notifiedRevision: Long = 0
) {
    val needsReminder: Boolean get() = status == "pending" && reminderEnabled && notifiedRevision != revision
}

@Entity(tableName = "monthly_reviews")
data class ReviewEntity(@PrimaryKey val month: String, val body: String, val statisticsSnapshot: String,
    val generatedAt: Long, val savedAt: Long = System.currentTimeMillis())

@Entity(tableName = "metadata")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "import_rows", primaryKeys = ["fileHash", "ordinal"])
data class ImportReceipt(val fileHash: String, val ordinal: Int, val transactionId: String)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAt DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM monthly_budgets") fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM payment_plans ORDER BY dueAt") fun observePlans(): Flow<List<PaymentPlan>>
    @Query("SELECT * FROM monthly_reviews") fun observeReviews(): Flow<List<ReviewEntity>>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAt DESC") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun transaction(id: String): TransactionEntity?
    @Upsert suspend fun saveTransaction(item: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id=:id") suspend fun deleteTransaction(id: String)
    @Upsert suspend fun saveBudget(item: BudgetEntity)
    @Query("SELECT * FROM monthly_budgets WHERE month=:month") suspend fun budget(month: String): BudgetEntity?
    @Query("SELECT * FROM payment_plans") suspend fun plans(): List<PaymentPlan>
    @Query("SELECT * FROM payment_plans WHERE id=:id") suspend fun plan(id: Long): PaymentPlan?
    @Insert suspend fun insertPlan(item: PaymentPlan): Long
    @Update suspend fun updatePlan(item: PaymentPlan)
    @Query("DELETE FROM payment_plans WHERE id=:id") suspend fun deletePlan(id: Long)
    @Upsert suspend fun saveReview(item: ReviewEntity)
    @Query("SELECT * FROM metadata WHERE `key`=:key") suspend fun meta(key: String): MetaEntity?
    @Upsert suspend fun setMeta(item: MetaEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedCategories(items: List<CategoryEntity>)
    @Query("SELECT * FROM import_rows WHERE fileHash=:hash") suspend fun receipts(hash: String): List<ImportReceipt>
    @Insert suspend fun saveReceipt(item: ImportReceipt)
}

@Database(entities = [CategoryEntity::class, TransactionEntity::class, BudgetEntity::class, PaymentPlan::class,
    ReviewEntity::class, MetaEntity::class, ImportReceipt::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() { abstract fun ledger(): LedgerDao }

val campusCategories = listOf(
    CategoryEntity("food", "餐饮", "expense", 0), CategoryEntity("shopping", "购物", "expense", 1),
    CategoryEntity("transport", "交通", "expense", 2), CategoryEntity("study", "学习", "expense", 3),
    CategoryEntity("bills", "生活缴费", "expense", 4), CategoryEntity("other", "其他支出", "expense", 5),
    CategoryEntity("allowance", "生活费", "income", 6), CategoryEntity("scholarship", "奖助学金", "income", 7),
    CategoryEntity("work", "兼职", "income", 8), CategoryEntity("other_income", "其他收入", "income", 9)
)

fun TransactionEntity.toEntry(): Entry = Entry(id, type, amountCents, LocalDate.ofEpochDay(dateEpochDay).toString(),
    campusCategories.first { it.code == categoryCode }.name, note)
fun Entry.toEntity(source: String = "csv", raw: String = ""): TransactionEntity = TransactionEntity(
    id, type, cents, LocalDate.parse(date).toEpochDay(), campusCategories.first { it.name == category && it.type == type }.code,
    note, source, raw)

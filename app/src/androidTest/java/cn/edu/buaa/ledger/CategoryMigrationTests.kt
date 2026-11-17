package cn.edu.buaa.ledger

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.edu.buaa.ledger.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CategoryMigrationTests {
    @Test fun versionOnePreservesEntriesAndSupportsOtherNames() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApp>()
        val name = "category-migration-test.db"
        app.deleteDatabase(name)
        try {
            SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name), null).use { old ->
                old.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`code` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`code`))")
                old.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `categoryCode` TEXT NOT NULL, `note` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `sourceText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`categoryCode`) REFERENCES `categories`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                old.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateEpochDay` ON `transactions` (`dateEpochDay`)")
                old.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryCode` ON `transactions` (`categoryCode`)")
                old.execSQL("CREATE TABLE IF NOT EXISTS `monthly_budgets` (`month` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, PRIMARY KEY(`month`))")
                old.execSQL("CREATE TABLE IF NOT EXISTS `payment_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `status` TEXT NOT NULL, `reminderEnabled` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `notifiedRevision` INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE IF NOT EXISTS `monthly_reviews` (`month` TEXT NOT NULL, `body` TEXT NOT NULL, `statisticsSnapshot` TEXT NOT NULL, `generatedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`month`))")
                old.execSQL("CREATE TABLE IF NOT EXISTS `metadata` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                old.execSQL("CREATE TABLE IF NOT EXISTS `import_rows` (`fileHash` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `transactionId` TEXT NOT NULL, PRIMARY KEY(`fileHash`, `ordinal`))")
                old.execSQL("INSERT INTO categories VALUES ('bills','生活缴费','expense',4)")
                old.execSQL("INSERT INTO transactions VALUES ('legacy','expense',1250,20774,'bills','original','manual','',123,456)")
                old.version = 1
            }
            val db = Room.databaseBuilder(app, LedgerDatabase::class.java, name)
                .addMigrations(LedgerDatabase.MIGRATION_1_2).build()
            try {
                val repo = LedgerRepository(app, db)
                repo.initialize()
                val old = repo.dao.transaction("legacy")!!
                assertEquals(1250L, old.amountCents)
                assertEquals(123L, old.createdAt)
                assertEquals("", old.customCategory)
                assertEquals("生活", old.toEntry().category)
                val custom = old.copy(id="custom",categoryCode="other",customCategory="社团活动")
                repo.saveEntry(custom)
                assertEquals("社团活动", repo.dao.transaction("custom")!!.customCategory)
                val csv = CsvCodec.encode(listOf(custom.toEntry()))
                assertEquals(custom.toEntry().fingerprint(), CsvCodec.decodeRow(CsvCodec.parse(csv)[1]).fingerprint())
            } finally { db.close() }
        } finally { app.deleteDatabase(name) }
    }
}

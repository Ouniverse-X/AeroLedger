package cn.edu.buaa.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class BackupAndQueryTests {
    private val app get()=ApplicationProvider.getApplicationContext<LedgerApp>()
    private suspend fun withRepo(block:suspend(LedgerRepository)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(app,LedgerDatabase::class.java).build()
        try {val repo=LedgerRepository(app,db);repo.dao.seedCategories(campusCategories);block(repo)}finally{db.close()}
    }
    @Test fun backupRoundTripAndConflictPolicies()=runBlocking {
        withRepo {source->
            val day=LocalDate.of(2026,11,4).toEpochDay()
            source.saveEntry(TransactionEntity("backup-one","expense",1250,day,"food","午餐","text","食堂午餐12.5元"))
            source.setBudget(YearMonth.of(2026,11),150000)
            source.dao.restorePlan(PaymentPlan(77,"备份已缴计划",5000,1760000000000,status="paid",reminderEnabled=false))
            source.dao.saveReview(ReviewEntity("2026-11","用户编辑过的回顾","真实统计依据",10,20))
            source.dao.setMeta(MetaEntity("demo_loaded","true"))
            source.dao.saveReceipt(ImportReceipt("a".repeat(64),2,"backup-one"))
            val original=source.createBackup("dark")
            val encoded=BackupCodec.encode(original)
            val decoded=BackupCodec.decode(encoded)
            assertEquals(original,decoded)
            withRepo {target->
                target.saveEntry(original.entries.single().copy(note="本机修改"))
                target.saveEntry(TransactionEntity("local-only","income",100,day,"work","仅在本机"))
                val preview=target.previewBackup(encoded,"backup.json")
                assertEquals(1,preview.conflicts);assertEquals(3,preview.newRecords)
                target.saveEntry(original.entries.single().copy(note="预览后修改"))
                target.restoreBackup(decoded,RestoreMode.KEEP_LOCAL)
                assertEquals("预览后修改",target.dao.transaction("backup-one")!!.note)
                assertNotNull(target.dao.transaction("local-only"))
                target.restoreBackup(decoded,RestoreMode.USE_BACKUP)
                assertEquals("午餐",target.dao.transaction("backup-one")!!.note)
                assertEquals("食堂午餐12.5元",target.dao.transaction("backup-one")!!.sourceText)
                target.restoreBackup(decoded,RestoreMode.REPLACE_ALL)
                assertNull(target.dao.transaction("local-only"))
                val restored=target.createBackup("dark")
                assertEquals(original.entries,restored.entries);assertEquals(original.budgets,restored.budgets)
                assertEquals(original.reviews,restored.reviews);assertEquals(original.receipts,restored.receipts)
                assertEquals("paid",restored.plans.single().status)
                assertTrue(restored.plans.single().revision>original.plans.single().revision)
                assertFalse(target.seedDemo())
                val second=target.previewBackup(encoded,"backup.json")
                assertEquals(0,second.newRecords)
                val before=target.dao.transactions()
                try{target.previewBackup(encoded.replace("AeroLedgerBackup","OtherFormat"),"bad.json");fail("Accepted wrong format")}
                catch(_:Exception){assertEquals(before,target.dao.transactions())}
                val root=org.json.JSONObject(encoded);root.put("data",root.getString("data").replace("午餐","已篡改"))
                try{target.previewBackup(root.toString(),"bad.json");fail("Accepted corrupt checksum")}
                catch(_:Exception){assertEquals(before,target.dao.transactions())}
                val empty=decoded.copy(entries=emptyList(),budgets=emptyList(),plans=emptyList(),reviews=emptyList(),metadata=emptyList(),receipts=emptyList())
                target.restoreBackup(BackupCodec.decode(BackupCodec.encode(empty)),RestoreMode.REPLACE_ALL)
                assertTrue(target.dao.transactions().isEmpty());assertTrue(target.dao.plans().isEmpty())
            }
        }
    }

    @Test fun sqlPagingSearchAndAggregatesWithTenThousandRecords()=runBlocking {
        withRepo {repo->
            val month=YearMonth.of(2026,11);val day=month.atDay(4).toEpochDay()
            val entries=(0 until 10000).map{TransactionEntity("bulk-$it","expense",1,day,if(it%2==0)"food" else "transport",if(it%2==0)"食堂 lunch" else "100%_通勤")}
            repo.dao.saveTransactions(entries)
            repo.saveEntry(TransactionEntity("outside","expense",5000,month.minusYears(3).atDay(1).toEpochDay(),"food","不在范围"))
            var window=repo.window(FilterSpec(month),50).first()
            assertEquals(50,window.entries.size);assertEquals(10000,window.filteredTotals!!.count)
            assertEquals(10000L,window.monthlyTotals!!.expense)
            assertEquals(10000L,window.categoryTotals!!.sumOf{it.amountCents})
            assertEquals(10000L,window.trendTotals!!.sumOf{it.expense})
            assertEquals(10000,window.dailyTotals.single().count);assertEquals(5,window.recentEntries!!.size)
            window=repo.window(FilterSpec(month,query="LUNCH"),100).first()
            assertEquals(100,window.entries.size);assertEquals(5000,window.filteredTotals!!.count)
            assertTrue(window.entries.all{it.categoryCode=="food"})
            assertEquals(5000,repo.filteredEntries(FilterSpec(month,query="%_")).size)
            assertEquals(10000,repo.filteredEntries(FilterSpec(month)).size)
            val next=repo.window(FilterSpec(month),100).first()
            assertEquals(100,next.entries.map{it.id}.toSet().size)
        }
    }

    @Test fun deleteUndoPreservesIdentityAndDoesNotOverwrite()=runBlocking {
        withRepo {repo->
            val item=TransactionEntity("undo-one","expense",1250,LocalDate.now().toEpochDay(),"food","撤销测试")
            repo.saveEntry(item);val saved=repo.dao.transaction(item.id)!!
            val deleted=repo.deleteForUndo(item.id)!!;assertNull(repo.dao.transaction(item.id))
            repo.undoDelete(deleted);assertEquals(saved,repo.dao.transaction(item.id))
            try{repo.undoDelete(deleted.copy(note="不应覆盖"));fail("Overwrote existing entry")}
            catch(_:IllegalArgumentException){assertEquals(saved,repo.dao.transaction(item.id))}
        }
    }

    @Test fun draftsAndPendingImportsSurviveStoreRecreation() {
        val key="test-${java.util.UUID.randomUUID()}"
        val directory=java.io.File(app.cacheDir,key).apply{mkdirs()}
        val context=object:android.content.ContextWrapper(app){
            override fun getFilesDir()=directory
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("$key-$name",mode)
        }
        val first=DraftStore(context)
        try {
            val values=mapOf("amount" to "12.50","note" to "未保存的中文草稿")
            first.save(key,values);assertEquals(values,DraftStore(context).read(key))
            first.saveImport("CSV source","账目.csv")
            assertEquals("CSV source" to "账目.csv",DraftStore(context).readImport())
            first.saveRestore("备份源文件","backup.json")
            assertEquals("备份源文件" to "backup.json",DraftStore(context).readRestore())
            first.clearImport();assertNull(DraftStore(context).readImport())
            first.clearRestore();assertNull(DraftStore(context).readRestore())
            first.clear(key);assertTrue(DraftStore(context).read(key).isEmpty())
        }finally{first.clear(key);context.deleteSharedPreferences("$key-editor_drafts");directory.deleteRecursively()}
    }
}

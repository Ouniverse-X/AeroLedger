package cn.edu.buaa.ledger

import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.reminder.PaymentReminders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class LedgerDeviceTests {
    private val app get()=ApplicationProvider.getApplicationContext<LedgerApp>()
    private suspend fun withDb(action:suspend(LedgerRepository)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(app,LedgerDatabase::class.java).build()
        try {val repo=LedgerRepository(app,db);repo.dao.seedCategories(campusCategories);action(repo)}finally{db.close()}
    }
    @Test fun roomBudgetsAndFilters()=runBlocking {
        withDb {repo->
            val m=YearMonth.of(2026,11)
            val a=TransactionEntity("a","expense",1250,m.atDay(4).toEpochDay(),"food","午餐")
            repo.saveEntry(a)
            repo.saveEntry(TransactionEntity("b","income",50000,m.atDay(4).toEpochDay(),"allowance","生活费"))
            repo.saveEntry(TransactionEntity("old","expense",600,m.minusMonths(1).atDay(31).toEpochDay(),"transport","公交"))
            repo.setBudget(m,1000)
            var state=repo.data.first()
            assertEquals(-250L,state.budget(m)-state.monthEntries(m).totals().expense)
            assertEquals(1,FilterSpec(m,category="food").select(state.entries).size)
            assertEquals(2,FilterSpec(m,day=m.atDay(4)).select(state.entries).size)
            repo.saveEntry(a.copy(amountCents=800));state=repo.data.first()
            assertEquals(3,state.entries.size);assertEquals(200L,state.budget(m)-state.monthEntries(m).totals().expense)
            repo.saveEntry(a.copy(type="income",categoryCode="work"));state=repo.data.first()
            assertEquals(0L,state.monthEntries(m).totals().expense)
            repo.dao.deleteTransaction("a");assertEquals(2,repo.dao.transactions().size)
            val review=ReviewEntity("2026-11","编辑后的回顾","snapshot",1234)
            repo.dao.saveReview(review);assertEquals(review,repo.dao.observeReviews().first().single())
        }
    }

    @Test fun csvPreviewTransactionsAndIdentity()=runBlocking {
        withDb {repo->
            val csv="id,type,amount,date,category,note\n"+
                "one,expense,12.50,2026-11-04,餐饮,午餐\n"+
                "one,expense,12.50,2026-11-04,餐饮,午餐\n"+
                "two,expense,12.50,2026-11-04,餐饮,午餐\n"+
                "bad,expense,-5,2026-11-04,餐饮,错误\n"+
                "bad2,expense,2,2026-02-30,交通,错误\n"
            val p=repo.previewCsv(csv,"test.csv")
            assertEquals(1,p.valid);assertEquals(1,p.duplicate);assertEquals(1,p.suspicious);assertEquals(2,p.invalid)
            assertTrue(repo.dao.transactions().isEmpty())
            val result=repo.commitImport(p,false);assertEquals(1,result.added);assertEquals(2,result.skipped)
            val again=repo.commitImport(repo.previewCsv(csv,"test.csv"),false);assertEquals(0,again.added)
            val kept=repo.commitImport(repo.previewCsv(csv,"test.csv"),true);assertEquals(1,kept.added)
            assertEquals(2,repo.dao.transactions().size)
            val roundtrip=CsvCodec.encode(repo.dao.transactions().map{it.toEntry()})
            assertEquals(2,repo.previewCsv(roundtrip,"export.csv").duplicate)
            try {repo.previewCsv("id,type,amount,date,category,note\nx,expense,1,2026-11-04,餐饮,\"broken","bad.csv");fail("accepted broken CSV")}
            catch(_:IllegalArgumentException){assertEquals(2,repo.dao.transactions().size)}
        }
    }

    @Test fun persistentDatabaseReopens()=runBlocking {
        val name="persistence-device-test.db";app.deleteDatabase(name)
        try {
            var db=Room.databaseBuilder(app,LedgerDatabase::class.java,name).build()
            db.ledger().seedCategories(campusCategories)
            db.ledger().saveBudget(BudgetEntity("2026-11",150000))
            db.ledger().saveTransaction(TransactionEntity("persist","expense",10,LocalDate.now().toEpochDay(),"food","persist"));db.close()
            db=Room.databaseBuilder(app,LedgerDatabase::class.java,name).build()
            assertEquals(150000L,db.ledger().budget("2026-11")!!.amountCents)
            assertEquals("persist",db.ledger().transactions().single().id);db.close()
        }finally{app.deleteDatabase(name)}
    }

    @Test fun javaPrototypeMigrationPreservesData()=runBlocking {
        val legacyName="prototype-migration-test.db";app.deleteDatabase(legacyName)
        val wrapper=object:android.content.ContextWrapper(app){
            override fun getDatabasePath(name:String)=super.getDatabasePath(if(name=="campus-ledger.db")legacyName else name)
        }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(legacyName),null).use { old ->
            old.execSQL("CREATE TABLE entries(id TEXT,type TEXT,cents INTEGER,date TEXT,category TEXT,note TEXT)")
            old.execSQL("INSERT INTO entries VALUES('old-id','expense',1250,'2026-11-04','餐饮','旧数据')")
            old.execSQL("CREATE TABLE budgets(month TEXT,cents INTEGER)");old.execSQL("INSERT INTO budgets VALUES('2026-11',150000)")
            old.execSQL("CREATE TABLE bills(name TEXT,cents INTEGER,due_at INTEGER,status TEXT,enabled INTEGER,notified INTEGER)")
            old.execSQL("INSERT INTO bills VALUES('电费',5000,1000,'paid',1,1)")
            old.execSQL("CREATE TABLE reviews(month TEXT,body TEXT)");old.execSQL("INSERT INTO reviews VALUES('2026-11','我的旧回顾')")
            old.execSQL("CREATE TABLE settings(name TEXT,value TEXT)");old.execSQL("INSERT INTO settings VALUES('demo_loaded','true')")
        }
        val db=Room.inMemoryDatabaseBuilder(app,LedgerDatabase::class.java).build()
        try {
            val repo=LedgerRepository(wrapper,db);repo.initialize();repo.initialize()
            assertEquals(1,repo.dao.transactions().size)
            assertEquals("旧数据",repo.dao.transaction("old-id")!!.note)
            assertEquals(150000L,repo.dao.budget("2026-11")!!.amountCents)
            assertEquals("paid",repo.dao.plans().single().status)
            assertEquals("我的旧回顾",repo.dao.observeReviews().first().single().body)
            assertFalse(repo.seedDemo())
        }finally{db.close();app.deleteDatabase(legacyName)}
    }

    @Test fun realAlarmNotificationAndCancellation()=runBlocking {
        app.ensureInitialized()
        assertTrue("Grant notification permission before device tests",PaymentReminders.allowed(app))
        assertTrue("Allow exact alarms before device tests",PaymentReminders.exactAllowed(app))
        val repo=app.repository;val manager=app.getSystemService(NotificationManager::class.java)
        val before=repo.dao.transactions().size
        val id=repo.savePlan(PaymentPlan(name="自动测试（示例）",amountCents=1,dueAt=System.currentTimeMillis()+2500))
        fun visible()=manager.activeNotifications.any{it.tag=="payment-$id"}
        suspend fun waitDelivered(){withTimeout(20000){while(repo.dao.plan(id)!!.needsReminder)delay(200)}}
        suspend fun waitVisible(expected:Boolean){withTimeout(5000){while(visible()!=expected)delay(50)}}
        try {
            waitDelivered();waitVisible(true);assertTrue(visible())
            var plan=repo.dao.plan(id)!!
            assertFalse(PaymentReminders.deliver(app,id,plan.revision))
            PaymentReminders.restore(app);assertTrue(visible())
            repo.savePlan(plan.copy(status="paid"));waitVisible(false);assertFalse(visible())
            assertFalse(PaymentReminders.deliver(app,id,plan.revision))
            plan=repo.dao.plan(id)!!;repo.savePlan(plan.copy(status="cancelled"))
            assertFalse(PaymentReminders.deliver(app,id,repo.dao.plan(id)!!.revision))
            repo.savePlan(repo.dao.plan(id)!!.copy(status="pending",reminderEnabled=false))
            assertFalse(PaymentReminders.deliver(app,id,repo.dao.plan(id)!!.revision))
            repo.savePlan(repo.dao.plan(id)!!.copy(reminderEnabled=true,dueAt=System.currentTimeMillis()+2000))
            val oldRevision=repo.dao.plan(id)!!.revision
            repo.savePlan(repo.dao.plan(id)!!.copy(dueAt=System.currentTimeMillis()+120000))
            assertFalse(PaymentReminders.deliver(app,id,oldRevision));delay(3000)
            assertFalse(visible());assertTrue(repo.dao.plan(id)!!.needsReminder)
            assertFalse(PaymentReminders.deliver(app,id,repo.dao.plan(id)!!.revision))
            repo.savePlan(repo.dao.plan(id)!!.copy(dueAt=System.currentTimeMillis()-1000))
            PaymentReminders.restore(app);waitDelivered();waitVisible(true);assertTrue(visible())
            repo.savePlan(repo.dao.plan(id)!!.copy(reminderEnabled=false));waitVisible(false);assertFalse(visible())
            assertEquals(before,repo.dao.transactions().size)
        }finally{PaymentReminders.cancel(app,id);repo.dao.deletePlan(id)}
    }
}

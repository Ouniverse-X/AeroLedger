package cn.edu.buaa.ledger

import android.app.Application
import cn.edu.buaa.ledger.data.LedgerRepository
import cn.edu.buaa.ledger.reminder.PaymentReminders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LedgerApp : Application() {
    val repository by lazy { LedgerRepository.create(this) }
    private val initialization = Mutex()
    private var ready = false
    suspend fun ensureInitialized() = initialization.withLock {
        if (!ready) { repository.initialize(); ready = true }
    }
    override fun onCreate() { super.onCreate(); PaymentReminders.createChannel(this) }
}

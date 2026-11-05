package cn.edu.buaa.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cn.edu.buaa.ledger.*
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.reminder.PaymentReminders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.YearMonth

class LedgerViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as LedgerApp
    val repository = app.repository
    val appearance = AppearanceStore(application)
    val theme = appearance.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val data = MutableStateFlow(LedgerData())
    val startupError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    var filter: FilterSpec
        get() = FilterSpec(YearMonth.parse(saved["month"] ?: YearMonth.now().toString()),
            saved.get<String>("day")?.let(java.time.LocalDate::parse), saved["type"], saved["category"])
        set(value) {
            saved["month"] = value.month.toString(); saved["day"] = value.day?.toString()
            saved["type"] = value.type; saved["category"] = value.category
        }
    private var loader: Job? = null
    init { load() }
    fun load() {
        loader?.cancel(); startupError.value = null
        loader = viewModelScope.launch {
            try { app.ensureInitialized(); PaymentReminders.restore(app); repository.data.collect { data.value = it } }
            catch (e: Exception) { if(e is CancellationException) throw e; startupError.value = "本地数据暂时无法读取：${e.message}" }
        }
    }
    fun task(action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { action() }
            catch (e: Exception) { if(e is CancellationException) throw e; messages.emit(e.message ?: "操作失败，请重试") }
            finally { busy.value = false }
        }
    }
    fun changeTheme(value: String) { viewModelScope.launch { appearance.setTheme(value) } }
    fun saveEntry(item: TransactionEntity, done: () -> Unit) = task { repository.saveEntry(item); messages.emit("账目已保存"); done() }
    fun deleteEntry(id: String) = task { repository.dao.deleteTransaction(id); messages.emit("账目已删除，统计已更新") }
    fun setBudget(month: YearMonth, cents: Long, done: () -> Unit) = task { repository.setBudget(month, cents); messages.emit("预算已更新"); done() }
    fun savePlan(plan: PaymentPlan, done: () -> Unit = {}) = task {
        repository.savePlan(plan); messages.emit(if(plan.status=="paid") "已缴并撤销提醒，未自动入账" else "计划已保存"); done()
    }
    fun seedDemo() = task { val added = repository.seedDemo(); messages.emit(if (added) "校园示例已载入，所有记录均为虚构" else "示例已载入过，不会重复添加") }
    fun restoreReminders() { viewModelScope.launch { try { PaymentReminders.restore(app) } catch(e: Exception) { messages.emit("提醒恢复失败，请重新打开应用重试") } } }
    fun saveReview(review: ReviewEntity, done: () -> Unit) = task { repository.dao.saveReview(review); messages.emit("回顾已保存"); done() }
}

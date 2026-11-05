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
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    val undoEntry = MutableStateFlow<TransactionEntity?>(null)
    val drafts = DraftStore(application)
    private val selectedFilter = MutableStateFlow(filter)
    private val pageLimit = MutableStateFlow(saved.get<Int>("pageLimit") ?: 50)
    var filter: FilterSpec
        get() = FilterSpec(YearMonth.parse(saved["month"] ?: YearMonth.now().toString()),
            saved.get<String>("day")?.let(java.time.LocalDate::parse), saved["type"], saved["category"],saved["query"] ?: "")
        set(value) {
            saved["month"] = value.month.toString(); saved["day"] = value.day?.toString()
            saved["type"] = value.type; saved["category"] = value.category
            saved["query"] = value.query
            if (selectedFilter.value != value) { pageLimit.value = 50; saved["pageLimit"] = 50; selectedFilter.value = value }
        }
    fun loadMore() { pageLimit.value += 50; saved["pageLimit"] = pageLimit.value }
    private var loader: Job? = null
    init { load() }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() {
        loader?.cancel(); startupError.value = null
        loader = viewModelScope.launch {
            try {
                app.ensureInitialized(); PaymentReminders.restore(app)
                withContext(Dispatchers.IO) { drafts.readImport() }?.let { (text,name) ->
                    runCatching { withContext(Dispatchers.IO) { repository.previewCsv(text,name) } }
                        .onSuccess { importPreview.value = it }
                        .onFailure { withContext(Dispatchers.IO) { drafts.clearImport() } }
                }
                withContext(Dispatchers.IO) { drafts.readRestore() }?.let { (text,name) ->
                    runCatching { repository.previewBackup(text,name) }.onSuccess { restorePreview.value=it }
                        .onFailure { withContext(Dispatchers.IO) { drafts.clearRestore() } }
                }
                combine(selectedFilter,pageLimit) { range,limit -> range to limit }
                    .flatMapLatest { (range,limit) -> repository.window(range,limit) }.collect { data.value = it }
            }
            catch (e: Exception) { if(e is CancellationException) throw e; startupError.value = "本地数据暂时无法读取：${e.message}" }
        }
    }
    fun task(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (e: Exception) { if(e is CancellationException) throw e; messages.emit(e.message ?: "操作失败，请重试") }
            finally { busy.value = false }
        }
    }
    fun changeTheme(value: String) { viewModelScope.launch { appearance.setTheme(value) } }
    fun saveEntry(item: TransactionEntity, done: () -> Unit) = task { repository.saveEntry(item); messages.emit("账目已保存"); done() }
    fun deleteEntry(id: String, done:()->Unit={}) = task { undoEntry.value = repository.deleteForUndo(id);done() }
    fun undoDelete() = task { undoEntry.value?.let { repository.undoDelete(it); undoEntry.value=null; messages.emit("已恢复账目") } }
    suspend fun previewImport(text: String, filename: String) {
        val preview = withContext(Dispatchers.IO) { repository.previewCsv(text,filename) }
        withContext(Dispatchers.IO) { drafts.saveImport(text,filename) }
        importPreview.value = preview
    }
    suspend fun completeImport(keep: Boolean): ImportOutcome {
        val result = repository.commitImport(requireNotNull(importPreview.value),keep)
        withContext(Dispatchers.IO) { drafts.clearImport() }; importPreview.value=null
        return result
    }
    fun discardImport() = task { withContext(Dispatchers.IO) { drafts.clearImport() }; importPreview.value=null }
    suspend fun previewRestore(text:String,filename:String) {
        val result=repository.previewBackup(text,filename)
        withContext(Dispatchers.IO){drafts.saveRestore(text,filename)}
        restorePreview.value=result
    }
    fun restore(mode: RestoreMode, restoreTheme: Boolean, done: () -> Unit) = task {
        val preview = requireNotNull(restorePreview.value)
        val count = repository.restoreBackup(preview.backup,mode)
        withContext(Dispatchers.IO) {
            drafts.clearRestore()
            if(mode==RestoreMode.REPLACE_ALL){drafts.clearEditors();drafts.clearImport()}
        }
        if(mode==RestoreMode.REPLACE_ALL)importPreview.value=null
        if (restoreTheme) runCatching { appearance.setTheme(preview.backup.theme) }
            .onFailure { messages.emit("账本已恢复，外观未能更新，请在设置中重试") }
        undoEntry.value=null; restorePreview.value=null
        messages.emit("已恢复 $count 项，提醒已重新核对"); done()
    }
    fun setBudget(month: YearMonth, cents: Long, done: () -> Unit) = task { repository.setBudget(month, cents); messages.emit("预算已更新"); done() }
    fun savePlan(plan: PaymentPlan, done: () -> Unit = {}) = task {
        repository.savePlan(plan); messages.emit(if(plan.status=="paid") "已缴并撤销提醒，未自动入账" else "计划已保存"); done()
    }
    fun seedDemo() = task { val added = repository.seedDemo(); messages.emit(if (added) "校园示例已载入，所有记录均为虚构" else "示例已载入过，不会重复添加") }
    fun restoreReminders() { viewModelScope.launch { try { PaymentReminders.restore(app) } catch(e: Exception) { messages.emit("提醒恢复失败，请重新打开应用重试") } } }
    fun saveReview(review: ReviewEntity, done: () -> Unit) = task { repository.dao.saveReview(review); messages.emit("回顾已保存"); done() }
}

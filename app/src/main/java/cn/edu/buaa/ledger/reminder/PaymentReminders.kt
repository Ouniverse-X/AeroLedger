package cn.edu.buaa.ledger.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import cn.edu.buaa.ledger.LedgerApp
import cn.edu.buaa.ledger.MainActivity
import cn.edu.buaa.ledger.R
import cn.edu.buaa.ledger.data.PaymentPlan
import cn.edu.buaa.ledger.domain.money
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

object PaymentReminders {
    const val CHANNEL = "campus_payments"
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "校园缴费提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "到期提醒待缴项目，不自动记账" })
    }
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun alarmIntent(context: Context, id: Long, revision: Long = 0): PendingIntent {
        val intent = Intent(context, PaymentReceiver::class.java).setData(Uri.parse("buaa-ledger://payment/$id"))
            .putExtra("plan_id", id).putExtra("revision", revision)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
        context.getSystemService(NotificationManager::class.java).cancel("payment-$id", 1)
    }
    fun schedule(context: Context, plan: PaymentPlan) {
        cancel(context, plan.id)
        if (!plan.needsReminder || !allowed(context)) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val time = maxOf(plan.dueAt, System.currentTimeMillis() + 1500)
        val pending = alarmIntent(context, plan.id, plan.revision)
        try {
            if (exactAllowed(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
        } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending) }
    }
    suspend fun restore(context: Context) {
        val app = context.applicationContext as LedgerApp
        app.ensureInitialized()
        val repo = app.repository
        repo.paymentMutex.withLock {
            for (plan in repo.dao.plans()) {
                if (plan.status == "pending" && plan.reminderEnabled && plan.notifiedRevision == plan.revision) continue
                schedule(context, plan)
            }
        }
    }
    suspend fun deliver(context: Context, id: Long, revision: Long): Boolean {
        val app = context.applicationContext as LedgerApp
        app.ensureInitialized()
        val repo = app.repository
        return repo.paymentMutex.withLock {
            val plan = repo.dao.plan(id) ?: return@withLock false
            if (plan.revision != revision || !plan.needsReminder || !allowed(context)) return@withLock false
            if (plan.dueAt > System.currentTimeMillis()) { schedule(context, plan); return@withLock false }
            val open = Intent(context, MainActivity::class.java).setData(Uri.parse("buaa-ledger://open/$id"))
                .putExtra("open_plan", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("缴费到期 · ${plan.name}").setContentText("待缴 ¥${plan.amountCents.money()} · 点击查看计划")
                .setContentIntent(tap).setAutoCancel(true).build()
            try {
                context.getSystemService(NotificationManager::class.java).notify("payment-$id", 1, notification)
                repo.dao.updatePlan(plan.copy(notifiedRevision = revision)); true
            } catch (_: SecurityException) { false }
        }
    }
    fun receive(receiver: BroadcastReceiver, context: Context, work: suspend () -> Unit) {
        val pending = receiver.goAsync()
        receiverScope.launch {
            try { withTimeout(8500) { work() } }
            catch (error: Exception) { android.util.Log.e("PaymentReminders", "提醒恢复失败，重新打开应用可重试", error) }
            finally { pending.finish() }
        }
    }
}

class PaymentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PaymentReminders.receive(this, context) { PaymentReminders.deliver(context, intent.getLongExtra("plan_id", -1), intent.getLongExtra("revision", -1)) }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
            PaymentReminders.receive(this, context) { PaymentReminders.restore(context) }
    }
}

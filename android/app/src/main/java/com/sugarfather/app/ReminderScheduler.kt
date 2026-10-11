package com.sugarfather.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {

    fun rescheduleAll(ctx: Context, reminders: List<Reminder>) {
        for (i in 0 until 2) {
            schedule(ctx, i, reminders.getOrNull(i) ?: Reminder())
        }
    }

    fun schedule(ctx: Context, index: Int, r: Reminder) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, index)
        am.cancel(pi)
        if (!r.enabled) return
        val next = nextTriggerMs(r.hour, r.minute)
        if (canExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun nextTriggerMs(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(hour, minute)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pending(ctx: Context, index: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("index", index)
        return PendingIntent.getBroadcast(
            ctx, 100 + index, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 到点：弹通知 + 排下一次 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra("index", 0)
        Repo.init(context)
        val r = Repo.reminders.getOrNull(index) ?: return
        if (!r.enabled) return
        Notify.showReminder(context, r.text, 200 + index)
        ReminderScheduler.schedule(context, index, r)
    }
}

/** 开机/更新后重排提醒；另含测试通道（通知栏可见性调试用） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Repo.init(context)
                ReminderScheduler.rescheduleAll(context, Repo.reminders)
            }
            "com.sugarfather.app.TEST_NOTIFY" -> {
                Repo.init(context)
                val t = Repo.reminders.firstOrNull { it.enabled }?.text ?: "该测血糖啦（测试）"
                Notify.showReminder(context, t, 299)
            }
        }
    }
}

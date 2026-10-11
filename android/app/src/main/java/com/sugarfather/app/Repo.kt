package com.sugarfather.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime

/** 单例状态仓库：Compose 直接观察这里的状态，写入即刷新界面 */
object Repo {

    private var db: Db? = null
    private var appContext: Context? = null

    var records by mutableStateOf<List<Record>>(emptyList())
        private set

    var targets by mutableStateOf(Targets())
        private set

    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (db == null) db = Db(ctx)
        refresh()
    }

    fun refresh() {
        val d = db ?: return
        records = d.allRecords()
        targets = SettingsCodec.decodeTargets(d.getSetting("targets"))
        reminders = SettingsCodec.decodeReminders(d.getSetting("reminders"))
    }

    fun appContextOrNull(): Context? = appContext

    fun addRecord(value: Double, period: String, note: String, measuredAt: Long): Record {
        val now = System.currentTimeMillis()
        val r = Record(
            genId(), Math.round(value * 10) / 10.0, period, note.take(200), measuredAt, now, now
        )
        db?.upsert(r)
        refresh()
        return r
    }

    fun updateRecord(id: String, value: Double, period: String, note: String, measuredAt: Long) {
        val old = records.firstOrNull { it.id == id } ?: return
        db?.upsert(
            old.copy(
                value = Math.round(value * 10) / 10.0,
                period = period,
                note = note.take(200),
                measuredAt = measuredAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun deleteRecord(id: String) {
        db?.delete(id)
        refresh()
    }

    fun saveTargets(t: Targets) {
        val norm = Targets(
            Math.round(t.fastingLo * 10) / 10.0,
            Math.round(t.fastingHi * 10) / 10.0,
            Math.round(t.nonFastingLo * 10) / 10.0,
            Math.round(t.nonFastingHi * 10) / 10.0
        )
        db?.setSetting("targets", SettingsCodec.encodeTargets(norm))
        targets = norm
    }

    fun saveReminders(list: List<Reminder>) {
        val norm = list.take(2)
        db?.setSetting("reminders", SettingsCodec.encodeReminders(norm))
        reminders = norm
        appContext?.let { ReminderScheduler.rescheduleAll(it, norm) }
    }

    fun importRecords(preview: Csv.ImportPreview) {
        val d = db ?: return
        d.runInTransaction {
            for (r in preview.toOverwrite) {
                val old = records.firstOrNull { it.id == r.id }
                d.upsert(r.copy(createdAt = old?.createdAt ?: r.createdAt))
            }
            for (r in preview.toAdd) {
                d.upsert(r)
            }
        }
        refresh()
    }

    fun existingKeys(): Set<String> = records.map { Csv.tripleKey(it) }.toSet()

    fun existingIds(): Set<String> = records.map { it.id }.toSet()

    fun exportCsvText(): String = Csv.exportCsv(records, targets, LocalDateTime.now())

    fun exportPlainText(): String = Csv.exportText(records, targets, LocalDateTime.now())

    private fun genId(): String {
        val t = java.lang.Long.toString(System.currentTimeMillis(), 36)
        val r = Integer.toString((Math.random() * 1_000_000).toInt() + 1, 36)
        return "r$t$r"
    }
}

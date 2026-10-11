package com.sugarfather.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 统计与业务规则纯函数（与小程序版 selftest 口径一致，可在 JVM 单测） */
object Stats {

    const val VALUE_MIN = 1.0
    const val VALUE_MAX = 33.3

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun toLocalDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone()).toLocalDate()

    fun toLocalDateTime(epochMs: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMs).atZone(zone()).toLocalDateTime()

    fun toEpochMs(dt: LocalDateTime): Long =
        dt.atZone(zone()).toInstant().toEpochMilli()

    fun statusOf(value: Double, tgt: String, targets: Targets): String {
        val (lo, hi) = targets.range(tgt)
        return when {
            value < lo -> "low"
            value > hi -> "high"
            else -> "ok"
        }
    }

    fun statusText(st: String): String = when (st) {
        "ok" -> "达标"
        "low" -> "偏低"
        else -> "偏高"
    }

    /** 两步录入第 1 步的数值校验（PRD FR-1：1.0–33.3，最多 1 位小数） */
    fun validateValue(s: String): ValueCheck {
        val t = s.trim()
        if (t.isEmpty()) return ValueCheck(false, Double.NaN, "请先输入血糖数值")
        if (!Regex("^\\d{1,2}(\\.\\d)?$").matches(t)) {
            return ValueCheck(false, Double.NaN, "格式不对，最多 1 位小数")
        }
        val v = t.toDouble()
        if (v < VALUE_MIN || v > VALUE_MAX) {
            return ValueCheck(false, v, "数值要在 1.0 到 33.3 之间")
        }
        return ValueCheck(true, Math.round(v * 10) / 10.0, "")
    }

    /** 按录入时刻所在时间窗推荐时段（PRD FR-2） */
    fun recommendPeriod(now: LocalDateTime): String {
        val h = now.hour + now.minute / 60.0
        for (p in PERIODS) {
            if (h >= p.winStart && h < p.winEnd) return p.id
        }
        return "random"
    }

    /** 已过截止点且今日无记录的时段（PRD FR-5） */
    fun missingPeriods(records: List<Record>, now: LocalDateTime): List<Period> {
        val h = now.hour + now.minute / 60.0
        val today = now.toLocalDate()
        val have = records
            .filter { toLocalDate(it.measuredAt) == today }
            .map { it.period }
            .toSet()
        return PERIODS.filter { p ->
            p.id != "random" && h >= p.due && p.id !in have
        }
    }

    /** 连续记录天数：今天没记时从昨天起算（PRD FR-4） */
    fun streakDays(records: List<Record>, today: LocalDate): Int {
        val set = records.map { toLocalDate(it.measuredAt) }.toSet()
        var d = if (today in set) today else today.minusDays(1)
        var n = 0
        while (d in set) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** 所选自然日范围内的记录（含今天） */
    fun recordsInRange(records: List<Record>, days: Int, today: LocalDate): List<Record> {
        val start = today.minusDays((days - 1).toLong())
        return records.filter { toLocalDate(it.measuredAt) >= start }
    }

    data class StatData(
        val avg: Double,
        val tir: Double,
        val count: Int,
        val max: Record,
        val min: Record
    )

    fun statOf(list: List<Record>, targets: Targets): StatData? {
        if (list.isEmpty()) return null
        var sum = 0.0
        var ok = 0
        var max = list[0]
        var min = list[0]
        for (r in list) {
            sum += r.value
            if (statusOf(r.value, periodById(r.period).tgt, targets) == "ok") ok++
            if (r.value > max.value) max = r
            if (r.value < min.value) min = r
        }
        return StatData(sum / list.size, ok.toDouble() / list.size, list.size, max, min)
    }

    data class PeriodAvg(val period: Period, val avg: Double?, val count: Int)

    fun periodAverages(list: List<Record>): List<PeriodAvg> = PERIODS.map { p ->
        val arr = list.filter { it.period == p.id }
        if (arr.isEmpty()) PeriodAvg(p, null, 0)
        else PeriodAvg(p, arr.sumOf { it.value } / arr.size, arr.size)
    }

    data class DayMean(val date: LocalDate, val avg: Double)

    fun dailyMeans(list: List<Record>): List<DayMean> {
        val byDay = LinkedHashMap<LocalDate, MutableList<Double>>()
        for (r in list) {
            val d = toLocalDate(r.measuredAt)
            byDay.getOrPut(d) { mutableListOf() }.add(r.value)
        }
        return byDay.entries
            .sortedBy { it.key }
            .map { DayMean(it.key, it.value.sum() / it.value.size) }
    }

    /* ---------- 展示格式 ---------- */

    private val WEEK = arrayOf("日", "一", "二", "三", "四", "五", "六")

    fun fmt1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    fun fmtMD(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"

    fun fmtHM(dt: LocalDateTime): String =
        "%02d:%02d".format(dt.hour, dt.minute)

    fun fmtDT(dt: LocalDateTime): String = "${fmtMD(dt.toLocalDate())} ${fmtHM(dt)}"

    fun fmtDayLabel(d: LocalDate): String = "${fmtMD(d)} 周${WEEK[d.dayOfWeek.value % 7]}"
}

data class ValueCheck(val ok: Boolean, val value: Double, val msg: String)

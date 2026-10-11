package com.sugarfather.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** CSV 导出/导入与文本导出（PRD FR-7），纯逻辑可单测 */
object Csv {

    const val BOM = "\uFEFF"
    private const val HEADER = "日期,时间,时段,血糖(mmol/L),判定,备注,记录ID"

    private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun quote(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }

    /** 导出 CSV（不含 BOM；写文件时加 Csv.BOM 前缀） */
    fun exportCsv(records: List<Record>, targets: Targets, now: LocalDateTime): String {
        val sb = StringBuilder()
        sb.append(HEADER).append("\r\n")
        for (r in records.sortedBy { it.measuredAt }) {
            val p = periodById(r.period)
            val st = Stats.statusOf(r.value, p.tgt, targets)
            val d = Stats.toLocalDateTime(r.measuredAt)
            sb.append(d.toLocalDate().toString()).append(',')
                .append(Stats.fmtHM(d)).append(',')
                .append(quote(p.label)).append(',')
                .append(fmt1(r.value)).append(',')
                .append(Stats.statusText(st)).append(',')
                .append(quote(r.note)).append(',')
                .append(quote(r.id)).append("\r\n")
        }
        return sb.toString()
    }

    /** 复制用纯文本（含汇总行） */
    fun exportText(records: List<Record>, targets: Targets, now: LocalDateTime): String {
        val lines = mutableListOf("【糖记】血糖记录导出", "导出时间 " + Stats.fmtDT(now))
        if (records.isEmpty()) {
            lines.add("暂无记录")
            return lines.joinToString("\n")
        }
        val sorted = records.sortedBy { it.measuredAt }
        val first = sorted.first().let { Stats.toLocalDate(it.measuredAt).toString() }
        val last = sorted.last().let { Stats.toLocalDate(it.measuredAt).toString() }
        lines.add("共 ${sorted.size} 条 · $first ~ $last")
        lines.add("---")
        for (r in sorted.reversed()) {
            val p = periodById(r.period)
            val st = Stats.statusOf(r.value, p.tgt, targets)
            val d = Stats.toLocalDateTime(r.measuredAt)
            val notePart = if (r.note.isNotEmpty()) "\t" + r.note else ""
            lines.add(
                "${Stats.toLocalDate(r.measuredAt)} ${Stats.fmtHM(d)}\t${p.label}\t${fmt1(r.value)}\t${Stats.statusText(st)}$notePart"
            )
        }
        val s = Stats.statOf(sorted, targets)
        if (s != null) {
            lines.add("---")
            lines.add(
                "平均 ${fmt1(s.avg)} mmol/L ｜ 达标比例 ${Math.round(s.tir * 100)}% ｜ 最高 ${fmt1(s.max.value)}" +
                    "（${Stats.fmtMD(Stats.toLocalDate(s.max.measuredAt))} ${periodById(s.max.period).label}）｜ 最低 ${fmt1(s.min.value)}" +
                    "（${Stats.fmtMD(Stats.toLocalDate(s.min.measuredAt))} ${periodById(s.min.period).label}）"
            )
        }
        lines.add(
            "目标：空腹 ${fmt1(targets.fastingLo)}–${fmt1(targets.fastingHi)} · 非空腹 ${fmt1(targets.nonFastingLo)}–${fmt1(targets.nonFastingHi)} mmol/L"
        )
        lines.add("仅供参考，不能代替医生诊断")
        return lines.joinToString("\n")
    }

    data class ImportPreview(
        val toAdd: List<Record>,
        val toOverwrite: List<Record>,
        val skipped: Int,
        val error: String?
    )

    /** 解析 CSV 行（支持引号包裹的逗号与双引号转义） */
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++
                    } else inQ = false
                } else cur.append(c)
            } else {
                when (c) {
                    '"' -> inQ = true
                    ',' -> { out.add(cur.toString()); cur.setLength(0) }
                    else -> cur.append(c)
                }
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    /**
     * 导入解析。去重规则（PRD FR-7）：同 id 覆盖；否则（测量时间+数值+时段）完全相同判为重复跳过。
     * existingKeys 元素格式： "measuredAt|value|period"（value 为 one-decimal 字符串）
     */
    fun parseCsv(
        content: String,
        existingIds: Set<String>,
        existingKeys: Set<String>,
        genId: () -> String,
        nowMs: () -> Long = { System.currentTimeMillis() }
    ): ImportPreview {
        val text = content.removePrefix(BOM)
        val lines = text.split("\n").map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportPreview(emptyList(), emptyList(), 0, "文件是空的")

        val start = if (lines[0].startsWith("日期")) 1 else 0
        val toAdd = mutableListOf<Record>()
        val toOverwrite = mutableListOf<Record>()
        var skipped = 0

        fun bad(row: Int, why: String) =
            ImportPreview(emptyList(), emptyList(), 0, "第 $row 行格式不正确（$why）")

        for (i in start until lines.size) {
            val rowNum = i + 1
            val f = splitCsvLine(lines[i])
            if (f.size < 5) return bad(rowNum, "列数不足")
            val date = runCatching { LocalDate.parse(f[0].trim()) }.getOrNull()
                ?: return bad(rowNum, "日期读不出来")
            val timeStr = f[1].trim()
            val time = runCatching {
                LocalTime.parse(if (timeStr.length == 4 && timeStr.contains(":")) "0$timeStr" else timeStr)
            }.getOrNull() ?: return bad(rowNum, "时间读不出来")
            val periodLabel = f[2].trim()
            val period = PERIODS.firstOrNull { it.label == periodLabel }?.id
                ?: return bad(rowNum, "时段「$periodLabel」不认识")
            val valueRaw = f[3].trim().toDoubleOrNull()
                ?: return bad(rowNum, "血糖数值读不出来")
            if (valueRaw < Stats.VALUE_MIN || valueRaw > Stats.VALUE_MAX) {
                return bad(rowNum, "血糖数值超出 1.0–33.3")
            }
            val v = Math.round(valueRaw * 10) / 10.0
            val note = f.getOrNull(5)?.trim().orEmpty().take(200)
            val id = f.getOrNull(6)?.trim().orEmpty()
            val measuredAt = Stats.toEpochMs(LocalDateTime.of(date, time))
            val key = minuteKey(measuredAt, v, period)

            when {
                id.isNotEmpty() && id in existingIds ->
                    toOverwrite += Record(id, v, period, note, measuredAt, nowMs(), nowMs())
                key in existingKeys -> skipped++
                else -> {
                    val newId = if (id.isNotEmpty()) id else genId()
                    toAdd += Record(newId, v, period, note, measuredAt, nowMs(), nowMs())
                }
            }
        }
        return ImportPreview(toAdd, toOverwrite, skipped, null)
    }

    /** 去重三元组 key：测量时间归一到分钟精度（CSV 只记录到分钟）+ 数值 + 时段 */
    fun tripleKey(r: Record): String = minuteKey(r.measuredAt, r.value, r.period)

    private fun minuteKey(measuredAt: Long, value: Double, period: String): String =
        "${measuredAt / 60000}|${fmt1(value)}|$period"
}

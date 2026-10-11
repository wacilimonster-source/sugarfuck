package com.sugarfather.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CsvTest {

    private val T = Targets()

    private fun at(h: Int, m: Int): LocalDateTime = LocalDateTime.of(2026, 10, 8, h, m)

    private fun rec(
        id: String,
        value: Double,
        period: String,
        h: Int,
        m: Int = 0,
        note: String = ""
    ): Record {
        val ms = Stats.toEpochMs(at(h, m))
        return Record(id, value, period, note, ms, ms, ms)
    }

    @Test
    fun exportShape() {
        val l = listOf(
            rec("a1", 6.5, "fasting", 7, 0, "走了一万步"),
            rec("a2", 9.1, "afterBreakfast", 9, 30)
        )
        val csv = Csv.exportCsv(l, T, at(12, 0))
        val lines = csv.split("\r\n").filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("日期,时间,时段,血糖(mmol/L),判定,备注,记录ID"))
        assertTrue(lines[1].contains("空腹,6.5,达标,走了一万步,a1"))
        assertTrue(lines[2].contains("早餐后,9.1,达标,,a2"))
    }

    @Test
    fun roundTripNoDuplicates() {
        val l = listOf(
            rec("a1", 6.5, "fasting", 7, 0, "备注,有逗号"),
            rec("a2", 9.1, "afterBreakfast", 9, 30, "引号\"测试\"")
        )
        val csv = Csv.exportCsv(l, T, at(12, 0))
        val preview = Csv.parseCsv(csv, emptySet(), emptySet(), { "new" })
        assertNull(preview.error)
        assertEquals(2, preview.toAdd.size)
        assertEquals(0, preview.skipped)
        val first = preview.toAdd.first { it.id == "a1" }
        assertEquals("备注,有逗号", first.note)
        assertEquals(6.5, first.value, 1e-9)
        assertEquals("fasting", first.period)
    }

    @Test
    fun dedupByIdThenTriple() {
        val l = listOf(rec("a1", 6.5, "fasting", 7, 0))
        val csv = Csv.exportCsv(l, T, at(12, 0))
        // 同 id → 覆盖
        val overwrite = Csv.parseCsv(csv, setOf("a1"), emptySet(), { "new" })
        assertEquals(1, overwrite.toOverwrite.size)
        assertEquals(0, overwrite.toAdd.size)
        // 无 id 但三元组相同 → 跳过
        val noId = "日期,时间,时段,血糖(mmol/L),判定,备注,记录ID\r\n" +
            "2026-10-08,07:00,空腹,6.5,达标,,\r\n"
        val skip = Csv.parseCsv(noId, emptySet(), setOf(Csv.tripleKey(l[0])), { "new" })
        assertEquals(1, skip.skipped)
        assertEquals(0, skip.toAdd.size)
    }

    @Test
    fun badLineReportsRow() {
        val bad = "日期,时间,时段,血糖(mmol/L),判定,备注,记录ID\r\n" +
            "2026-13-99,07:00,空腹,6.5,达标,,\r\n"
        val p = Csv.parseCsv(bad, emptySet(), emptySet(), { "new" })
        assertNotNull(p.error)
        assertTrue(p.error!!.contains("第 2 行"))
        val badPeriod = "日期,时间,时段,血糖(mmol/L),判定,备注,记录ID\r\n" +
            "2026-10-08,07:00,宵夜后,6.5,达标,,\r\n"
        assertTrue(Csv.parseCsv(badPeriod, emptySet(), emptySet(), { "new" }).error!!.contains("宵夜后"))
    }

    @Test
    fun csvQuoteParsing() {
        assertEquals(listOf("a,b", "c", "d\"e"), Csv.splitCsvLine("\"a,b\",c,\"d\"\"e\""))
        assertEquals(listOf("1", "2", "3"), Csv.splitCsvLine("1,2,3"))
    }

    @Test
    fun textExportSummary() {
        val l = listOf(rec("a1", 6.5, "fasting", 7, 0), rec("a2", 9.1, "afterBreakfast", 9, 30))
        val text = Csv.exportText(l, T, at(12, 0))
        assertTrue(text.startsWith("【糖记】血糖记录导出"))
        assertTrue(text.contains("共 2 条"))
        assertTrue(text.contains("平均 7.8"))
        assertTrue(text.contains("达标比例 100%"))
        assertTrue(text.contains("目标：空腹 4.4–7.0"))
        assertTrue(text.contains("仅供参考"))
        val empty = Csv.exportText(emptyList(), T, at(12, 0))
        assertTrue(empty.contains("暂无记录"))
    }
}

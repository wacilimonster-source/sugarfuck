package com.sugarfather.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StatsTest {

    private val T = Targets()

    private fun at(h: Int, m: Int): LocalDateTime = LocalDateTime.of(2026, 10, 8, h, m)

    private fun rec(
        value: Double,
        period: String,
        h: Int,
        m: Int = 0,
        dayOffset: Long = 0,
        note: String = ""
    ): Record {
        val dt = at(h, m).minusDays(dayOffset)
        return Record("t-$value-$period-$h-$m-$dayOffset", value, period, note, Stats.toEpochMs(dt), 0, 0)
    }

    @Test
    fun statusBoundaries() {
        assertEquals("low", Stats.statusOf(4.3, "fasting", T))
        assertEquals("ok", Stats.statusOf(4.4, "fasting", T))
        assertEquals("ok", Stats.statusOf(7.0, "fasting", T))
        assertEquals("high", Stats.statusOf(7.1, "fasting", T))
        assertEquals("ok", Stats.statusOf(10.0, "nonFasting", T))
        assertEquals("high", Stats.statusOf(10.1, "nonFasting", T))
        assertEquals("low", Stats.statusOf(4.39, "nonFasting", T))
    }

    @Test
    fun validateValueCases() {
        assertTrue(Stats.validateValue("6.5").ok)
        assertEquals(6.5, Stats.validateValue("6.5").value, 1e-9)
        assertTrue(Stats.validateValue("6").ok)
        assertTrue(!Stats.validateValue("").ok)
        assertTrue(!Stats.validateValue("6.55").ok)
        assertTrue(!Stats.validateValue("0.5").ok)
        assertTrue(!Stats.validateValue("34").ok)
        assertTrue(!Stats.validateValue("abc").ok)
        assertTrue(Stats.validateValue("33.3").ok)
        assertTrue(Stats.validateValue("1.0").ok)
    }

    @Test
    fun recommendPeriodCases() {
        assertEquals("random", Stats.recommendPeriod(at(2, 0)))
        assertEquals("fasting", Stats.recommendPeriod(at(6, 30)))
        assertEquals("afterBreakfast", Stats.recommendPeriod(at(9, 30)))
        assertEquals("beforeLunch", Stats.recommendPeriod(at(12, 0)))
        assertEquals("beforeDinner", Stats.recommendPeriod(at(16, 29)))
        assertEquals("bedtime", Stats.recommendPeriod(at(22, 30)))
    }

    @Test
    fun missingPeriodsCases() {
        val r = rec(6.0, "fasting", 7)
        assertTrue(Stats.missingPeriods(listOf(r), at(10, 0)).isEmpty())
        assertEquals(
            listOf("afterBreakfast"),
            Stats.missingPeriods(listOf(r), at(11, 30)).map { it.id }
        )
        assertEquals(
            listOf("afterBreakfast", "beforeLunch", "afterLunch"),
            Stats.missingPeriods(listOf(r), at(16, 30)).map { it.id }
        )
        val yesterday = rec(6.0, "fasting", 7, 0, 1)
        assertEquals(
            listOf("fasting"),
            Stats.missingPeriods(listOf(yesterday), at(10, 0)).map { it.id }
        )
        val all = listOf(
            rec(6.0, "fasting", 7), rec(6.0, "afterBreakfast", 9), rec(6.0, "beforeLunch", 12),
            rec(6.0, "afterLunch", 14), rec(6.0, "beforeDinner", 17), rec(6.0, "afterDinner", 20),
            rec(6.0, "bedtime", 22)
        )
        assertTrue(Stats.missingPeriods(all, at(23, 0)).isEmpty())
    }

    @Test
    fun streakCases() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals(
            2,
            Stats.streakDays(listOf(rec(6.0, "random", 8, 0, 1), rec(6.0, "random", 8, 0, 2)), today)
        )
        assertEquals(
            3,
            Stats.streakDays(
                listOf(
                    rec(6.0, "random", 8, 0, 0),
                    rec(6.0, "random", 8, 0, 1),
                    rec(6.0, "random", 8, 0, 2)
                ), today
            )
        )
        assertEquals(
            1,
            Stats.streakDays(listOf(rec(6.0, "random", 8, 0, 0), rec(6.0, "random", 8, 0, 2)), today)
        )
        assertEquals(0, Stats.streakDays(emptyList(), today))
    }

    @Test
    fun statAndRangeCases() {
        val l = listOf(rec(5.0, "random", 8), rec(15.0, "afterDinner", 20), rec(3.0, "fasting", 7))
        val s = Stats.statOf(l, T)!!
        assertEquals(23.0 / 3, s.avg, 1e-9)
        assertEquals(33, Math.round(s.tir * 100).toInt())
        assertEquals(15.0, s.max.value, 0.0)
        assertEquals(3.0, s.min.value, 0.0)
        assertNull(Stats.statOf(emptyList(), T))

        val withOld = l + rec(8.0, "random", 8, 0, 8)
        assertEquals(3, Stats.recordsInRange(withOld, 7, LocalDate.of(2026, 10, 8)).size)
        assertEquals(4, Stats.recordsInRange(withOld, 90, LocalDate.of(2026, 10, 8)).size)
    }

    @Test
    fun periodAvgAndMeans() {
        val l = listOf(rec(5.0, "fasting", 7), rec(7.0, "fasting", 7, 30), rec(9.0, "afterDinner", 20))
        val pa = Stats.periodAverages(l)
        val fasting = pa.first { it.period.id == "fasting" }
        assertEquals(2, fasting.count)
        assertEquals(6.0, fasting.avg!!, 1e-9)
        assertNull(pa.first { it.period.id == "beforeLunch" }.avg)

        val means = Stats.dailyMeans(l)
        assertEquals(1, means.size)
        assertEquals(7.0, means[0].avg, 1e-9)

        val twoDays = l + rec(8.0, "random", 8, 0, 1)
        assertEquals(2, Stats.dailyMeans(twoDays).size)
    }

    @Test
    fun settingsCodecRoundTrip() {
        val t = Targets(4.0, 8.0, 4.5, 11.0)
        val decoded = SettingsCodec.decodeTargets(SettingsCodec.encodeTargets(t))
        assertEquals(t, decoded)
        assertEquals(Targets(), SettingsCodec.decodeTargets(null))
        assertEquals(Targets(), SettingsCodec.decodeTargets("坏了的数据"))

        val rs = listOf(Reminder(true, 7, 0, "该测空腹血糖啦"), Reminder(false, 21, 30, "睡前别忘了测血糖"))
        assertEquals(rs, SettingsCodec.decodeReminders(SettingsCodec.encodeReminders(rs)))
        assertEquals(2, SettingsCodec.decodeReminders(null).size)
    }

    @Test
    fun chartGeometry() {
        val l = listOf(rec(5.0, "random", 8), rec(15.0, "afterDinner", 20), rec(3.0, "fasting", 7))
        val geo = Chart.compute(l, T, 320f, 250f, 7, false)!!
        assertEquals(3, geo.points.size)
        assertEquals("high", geo.points[1].st)
        assertTrue(geo.bandTop < geo.bandBottom)
        assertTrue(geo.fastingLineY != null)
        assertTrue(geo.yTicks.isNotEmpty())
        assertTrue(geo.xTicks.isNotEmpty())
        val mini = Chart.compute(l, T, 320f, 84f, 7, true)!!
        assertNull(mini.fastingLineY)
        assertTrue(mini.yTicks.isEmpty())
        assertNull(Chart.compute(emptyList(), T, 320f, 250f, 7, false))
    }
}

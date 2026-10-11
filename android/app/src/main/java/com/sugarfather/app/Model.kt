package com.sugarfather.app

/** 一条血糖记录 */
data class Record(
    val id: String,
    val value: Double,
    val period: String,
    val note: String,
    val measuredAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

/** 时段定义（PRD FR-2） */
data class Period(
    val id: String,
    val label: String,
    val tgt: String,
    val winStart: Int,
    val winEnd: Int,
    val due: Double
)

val PERIODS = listOf(
    Period("fasting", "空腹", "fasting", 4, 9, 9.5),
    Period("afterBreakfast", "早餐后", "nonFasting", 9, 11, 11.0),
    Period("beforeLunch", "午餐前", "nonFasting", 11, 13, 13.0),
    Period("afterLunch", "午餐后", "nonFasting", 13, 16, 16.0),
    Period("beforeDinner", "晚餐前", "nonFasting", 16, 19, 19.0),
    Period("afterDinner", "晚餐后", "nonFasting", 19, 22, 22.0),
    Period("bedtime", "睡前", "nonFasting", 22, 24, 24.0),
    Period("random", "随机", "nonFasting", 0, 4, 25.0)
)

fun periodById(id: String): Period = PERIODS.firstOrNull { it.id == id } ?: PERIODS.last()

/** 目标范围（PRD FR-3） */
data class Targets(
    val fastingLo: Double = 4.4,
    val fastingHi: Double = 7.0,
    val nonFastingLo: Double = 4.4,
    val nonFastingHi: Double = 10.0
) {
    fun range(tgt: String): Pair<Double, Double> =
        if (tgt == "fasting") fastingLo to fastingHi else nonFastingLo to nonFastingHi
}

/** 每日提醒（PRD FR-6，最多 2 条） */
data class Reminder(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val text: String = "该测血糖啦"
)

val REMINDER_TEXTS = listOf(
    "该测空腹血糖啦",
    "该测餐后血糖啦",
    "睡前别忘了测血糖",
    "记得测血糖哦"
)

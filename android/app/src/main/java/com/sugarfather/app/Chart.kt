package com.sugarfather.app

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class ChartPoint(val x: Float, val y: Float, val record: Record, val st: String)

class TrendGeometry(
    val points: List<ChartPoint>,
    val yTicks: List<Pair<Float, String>>,
    val xTicks: List<Pair<Float, String>>,
    val bandTop: Float,
    val bandBottom: Float,
    val fastingLineY: Float?,
    val fastingLabel: String?,
    val meanPath: List<Pair<Float, Float>>,
    val width: Float,
    val height: Float,
    val padL: Float,
    val padR: Float
)

/** 手写折线图（PRD FR-4）：目标区间带 + 空腹上限虚线 + 日均值折线 + 逐条着色数据点 */
object Chart {

    fun compute(
        list: List<Record>,
        targets: Targets,
        width: Float,
        height: Float,
        days: Int,
        mini: Boolean
    ): TrendGeometry? {
        if (list.isEmpty() || width <= 0f || height <= 0f) return null
        val padL = if (mini) 8f else 40f
        val padR = if (mini) 8f else 14f
        val padT = if (mini) 8f else 20f
        val padB = if (mini) 8f else 32f

        val lo = min(3.0, list.minOf { it.value } - 0.5)
        val hi = max(12.0, list.maxOf { it.value } + 0.5)
        val x0 = list.first().measuredAt.toDouble()
        val x1 = list.last().measuredAt.toDouble()
        val span = if (x1 - x0 == 0.0) 1.0 else x1 - x0

        fun xOf(t: Long): Float =
            (padL + (t - x0) / span * (width - padL - padR)).toFloat()

        fun yOf(v: Double): Float =
            (height - padB - (v - lo) / (hi - lo) * (height - padT - padB)).toFloat()

        val points = list.map {
            ChartPoint(
                xOf(it.measuredAt), yOf(it.value), it,
                Stats.statusOf(it.value, periodById(it.period).tgt, targets)
            )
        }

        val (nfLo, nfHi) = targets.range("nonFasting")
        val (_, fHi) = targets.range("fasting")

        val yTicks = mutableListOf<Pair<Float, String>>()
        if (!mini) {
            val step = if (hi - lo > 8) 4 else 2
            var v = ceil(lo).toInt()
            while (v <= hi) {
                yTicks.add(yOf(v.toDouble()) to v.toString())
                v += step
            }
        }

        val xTicks = mutableListOf<Pair<Float, String>>()
        if (!mini && list.isNotEmpty()) {
            val ticks = min(if (days > 60) 6 else 4, list.size)
            for (i in 0 until ticks) {
                val idx = (i.toDouble() * (list.size - 1) / max(1, ticks - 1)).toInt()
                val d = Stats.toLocalDate(list[idx].measuredAt)
                xTicks.add(xOf(list[idx].measuredAt) to "${d.monthValue}/${d.dayOfMonth}")
            }
        }
        // 同一天数据时避免重复标签
        val xTicksDedup = xTicks.distinctBy { it.second }

        val meanPath = Stats.dailyMeans(list).map { m ->
            val noon = Stats.toEpochMs(m.date.atTime(12, 0))
            xOf(noon) to yOf(m.avg)
        }

        val label = if (mini) null
        else "空腹上限 " + String.format(java.util.Locale.US, "%.1f", fHi)

        return TrendGeometry(
            points = points,
            yTicks = yTicks,
            xTicks = xTicksDedup,
            bandTop = yOf(nfHi),
            bandBottom = yOf(nfLo),
            fastingLineY = if (mini) null else yOf(fHi),
            fastingLabel = label,
            meanPath = meanPath,
            width = width,
            height = height,
            padL = padL,
            padR = padR
        )
    }
}

fun DrawScope.renderTrend(geo: TrendGeometry, mini: Boolean) {
    val bandW = geo.width - geo.padL - geo.padR
    val bandH = geo.bandBottom - geo.bandTop

    drawRect(
        color = Color(0x1C0E9C73),
        topLeft = Offset(geo.padL, geo.bandTop),
        size = Size(bandW, bandH)
    )

    if (!mini) {
        val textSizePx = 13.sp.toPx()
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = textSizePx
            color = android.graphics.Color.parseColor("#6A7280")
        }
        val canvas = drawContext.canvas.nativeCanvas
        paint.textAlign = Paint.Align.RIGHT
        for ((y, label) in geo.yTicks) {
            drawLine(
                color = Color(0xFFEEF1F5),
                start = Offset(geo.padL, y),
                end = Offset(geo.width - geo.padR, y),
                strokeWidth = 1.5f
            )
            canvas.drawText(label, geo.padL - 8f, y + 5f, paint)
        }
        geo.fastingLineY?.let { fy ->
            drawLine(
                color = PrimaryColor.copy(alpha = 0.65f),
                start = Offset(geo.padL, fy),
                end = Offset(geo.width - geo.padR, fy),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
            )
            geo.fastingLabel?.let {
                paint.color = android.graphics.Color.parseColor("#0B7D5E")
                canvas.drawText(it, geo.width - geo.padR, fy - 6f, paint)
            }
        }
        paint.color = android.graphics.Color.parseColor("#6A7280")
        for ((x, label) in geo.xTicks) {
            // 首尾标签贴边对齐，避免与 y 轴刻度在角落重叠
            paint.textAlign = when {
                x < geo.padL + 30f -> Paint.Align.LEFT
                x > geo.width - geo.padR - 30f -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(label, x, geo.height - 8f, paint)
        }
    }

    if (geo.meanPath.size > 1) {
        val linePath = Path()
        geo.meanPath.forEachIndexed { i, (x, y) ->
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        // 渐变面积（V2.2 设计稿：日均线下方柔和渐变）
        val bottomY = geo.height - if (mini) 2f else 26f
        val topY = geo.meanPath.minOf { it.second }
        val areaPath = Path()
        val first = geo.meanPath.first()
        val last = geo.meanPath.last()
        areaPath.moveTo(first.first, first.second)
        geo.meanPath.drop(1).forEach { (x, y) -> areaPath.lineTo(x, y) }
        areaPath.lineTo(last.first, bottomY)
        areaPath.lineTo(first.first, bottomY)
        areaPath.close()
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(OkColor.copy(alpha = 0.20f), OkColor.copy(alpha = 0f)),
                startY = topY,
                endY = bottomY
            )
        )
        drawPath(
            path = linePath,
            color = PrimaryColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    val radius = if (mini) 5f else if (geo.points.size > 60) 3.5f else 6f
    for (p in geo.points) {
        drawCircle(color = statusColor(p.st), radius = radius, center = Offset(p.x, p.y))
        drawCircle(
            color = Color.White,
            radius = radius,
            center = Offset(p.x, p.y),
            style = Stroke(width = 1.5f)
        )
    }
}

package com.sugarfather.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun TrendScreen(onEdit: (String) -> Unit) {
    val records = Repo.records
    val targets = Repo.targets
    var days by remember { mutableIntStateOf(7) }
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }

    val list = Stats.recordsInRange(records, days, LocalDate.now())
    val st = Stats.statOf(list, targets)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // 范围切换（选中白胶囊 + 投影）
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE9EDF1))
                .padding(5.dp)
        ) {
            listOf(7, 30, 90).forEach { d ->
                val on = days == d
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(12.dp)) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable { days = d; detail = null }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "近${d}天", fontSize = 16.5.sp,
                        color = if (on) Ink else SubColor,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 趋势图
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("血糖趋势", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("近${days}天 · ${list.size} 次", fontSize = 15.sp, color = SubColor)
            }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                DashedEmpty(null, "这段时间还没有记录")
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
                    val w = constraints.maxWidth.toFloat()
                    val h = constraints.maxHeight.toFloat()
                    val geo = remember(list, w, h, days) {
                        Chart.compute(list, targets, w, h, days, false)
                    }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(geo) {
                                detectTapGestures { off ->
                                    val g = geo ?: return@detectTapGestures
                                    val threshold = 40.dp.toPx()
                                    val hit = g.points.minByOrNull { p ->
                                        val dx = p.x - off.x
                                        val dy = p.y - off.y
                                        dx * dx + dy * dy
                                    }
                                    if (hit != null) {
                                        val dx = hit.x - off.x
                                        val dy = hit.y - off.y
                                        if (abs(dx) <= threshold && abs(dy) <= threshold) {
                                            val r = hit.record
                                            val p = periodById(r.period)
                                            detail = "${Stats.fmtDT(Stats.toLocalDateTime(r.measuredAt))} · ${p.label} · " +
                                                "${Stats.fmt1(r.value)} mmol/L ${Stats.statusText(hit.st)}" +
                                                if (r.note.isNotEmpty()) " · ${r.note}" else ""
                                        }
                                    }
                                }
                            }
                    ) {
                        geo?.let { renderTrend(it, mini = false) }
                    }
                }
                Text(
                    detail ?: "点图上的圆点，看那一次的详情",
                    fontSize = 15.5.sp, color = SubColor, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }

        // 统计卡（图标 + 数值层级）
        if (st != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    R.drawable.ic_scale, OkBg, PrimaryColor,
                    Stats.fmt1(st.avg), " mmol/L", "平均血糖", Modifier.weight(1f)
                )
                StatCard(
                    R.drawable.ic_target, Color(0xFFFFF4E2), AmberColor,
                    "${Math.round(st.tir * 100)}", " %", "达标比例", Modifier.weight(1f)
                )
                StatCard(
                    R.drawable.ic_updown, Color(0xFFE8F0FD), Color(0xFF1D4ED8),
                    "${Stats.fmt1(st.max.value)} / ${Stats.fmt1(st.min.value)}", " mmol/L",
                    "最高 / 最低", Modifier.weight(1f), small = true
                )
            }
            Text(
                "共 ${st.count} 次记录 · 连续记录 ${Stats.streakDays(records, LocalDate.now())} 天\n达标比例 = 在目标范围内的次数占比",
                fontSize = 15.sp, color = SubColor, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            )

            // 各时段平均
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("各时段平均", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("近${days}天", fontSize = 15.sp, color = SubColor)
                }
                Spacer(Modifier.height(6.dp))
                val pas = Stats.periodAverages(list)
                var maxAvg = 0.0
                pas.forEach { x -> x.avg?.let { if (it > maxAvg) maxAvg = it } }
                val cap = maxOf(10.0, Math.ceil(maxAvg))
                pas.forEach { x ->
                    Row(
                        Modifier.fillMaxWidth().height(60.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            x.period.label, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold,
                            color = Ink, modifier = Modifier.width(76.dp)
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEEF1F5))
                        ) {
                            if (x.avg != null) {
                                val pst = Stats.statusOf(x.avg, x.period.tgt, targets)
                                Box(
                                    Modifier
                                        .fillMaxWidth((x.avg / cap).toFloat().coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusColor(pst))
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (x.avg == null) "还没记过"
                            else "${Stats.fmt1(x.avg)} (${x.count}次)",
                            fontSize = 14.5.sp, color = if (x.avg == null) SubColor else Ink,
                            modifier = Modifier.width(118.dp), textAlign = TextAlign.End
                        )
                    }
                }
                Text(
                    "「空腹」按空腹目标判断，其余按非空腹目标判断",
                    fontSize = 15.sp, color = SubColor, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // 历史记录（日期胶囊分组）
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("历史记录", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("共 ${records.size} 条", fontSize = 15.sp, color = SubColor)
            }
            if (records.isEmpty()) {
                DashedEmpty(null, "还没有记录", "回「今天」页点 ＋ 记一笔", compact = true)
            } else {
                val byDay = records.sortedByDescending { it.measuredAt }
                    .groupBy { Stats.toLocalDate(it.measuredAt) }
                val keys = byDay.keys.sortedDescending()
                val shown = if (expanded) keys else keys.take(7)
                shown.forEach { d ->
                    Box(
                        Modifier
                            .padding(top = 14.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFEEF1F5))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            Stats.fmtDayLabel(d), fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold, color = SubColor
                        )
                    }
                    byDay[d]?.forEach { r -> RecordRow(r, targets) { onEdit(r.id) } }
                }
                if (keys.size > 7) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE3EFE9))
                            .clickable { expanded = !expanded }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (expanded) "收起 ▴" else "展开更多记录 ▾",
                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryColor
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun StatCard(
    @DrawableRes icon: Int,
    iconBg: Color,
    iconTint: Color,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            VIcon(icon, iconTint, 18.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            value, fontSize = if (small) 18.sp else 24.sp, fontWeight = FontWeight.ExtraBold,
            color = Ink, maxLines = 1
        )
        Text(unit.trim(), fontSize = 13.sp, color = SubColor, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 14.sp, color = SubColor, modifier = Modifier.padding(top = 3.dp))
    }
}

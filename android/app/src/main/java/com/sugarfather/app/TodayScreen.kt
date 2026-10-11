package com.sugarfather.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun TodayScreen(
    onEdit: (String) -> Unit,
    goTrend: () -> Unit,
    onQuickRecord: (String) -> Unit,
    onUpdate: () -> Unit
) {
    val records = Repo.records
    val targets = Repo.targets
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val miss = Stats.missingPeriods(records, now)
    val streak = Stats.streakDays(records, today)
    val latest = records.lastOrNull()
    val todays = records
        .filter { Stats.toLocalDate(it.measuredAt) == today }
        .sortedByDescending { it.measuredAt }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        // 头部：日期 + 连续天数（白胶囊 + 火苗圆标）
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Stats.fmtMD(today), fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold, color = Ink
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "周${arrayOf("日", "一", "二", "三", "四", "五", "六")[today.dayOfWeek.value % 7]}",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SubColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .shadow(2.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(start = 8.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).background(Color(0xFFFFF4E2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    VIcon(R.drawable.ic_flame, AmberColor, 16.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text("连续 $streak 天", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF8A4106))
            }
        }

        RemindCard(miss = miss, streak = streak, noRecords = records.isEmpty(), onQuickRecord = onQuickRecord)

        // 发现新版本提示（启动时静默检查到才显示）
        UpdateState.available?.let { info ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE8F0FD))
                    .border(1.5.dp, Color(0xFFC9DCFA), RoundedCornerShape(16.dp))
                    .clickable(onClick = onUpdate)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    VIcon(R.drawable.ic_download, Color(0xFF1D4ED8), 21.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "发现新版本 V${info.versionName}",
                        fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink
                    )
                    Text("点这里更新", fontSize = 14.5.sp, color = SubColor)
                }
                VIcon(R.drawable.ic_chev_r, ChevGray, 18.dp)
            }
        }

        if (latest != null) {
            HeroCard(latest, targets) { onEdit(latest.id) }
        } else {
            CardBox { DashedEmpty(R.drawable.ic_drop_outline, "还没有记录", "点下面的 ＋ 记第一笔") }
        }

        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("今日记录", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.weight(1f))
                if (todays.isNotEmpty()) {
                    Text("共 ${todays.size} 条", fontSize = 15.sp, color = SubColor)
                }
            }
            if (todays.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                DashedEmpty(null, "今天还没有记录", "测完血糖点下方 ＋，很快记完", compact = true)
            } else {
                todays.forEach { r -> RecordRow(r, targets) { onEdit(r.id) } }
            }
        }

        MiniTrendCard(records, targets, goTrend)

        Spacer(Modifier.height(14.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindCard(
    miss: List<Period>,
    streak: Int,
    noRecords: Boolean,
    onQuickRecord: (String) -> Unit
) {
    val done = noRecords || miss.isEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (done) OkBg else Color(0xFFFFF4E2))
            .border(1.5.dp, if (done) Color(0xFFC4E8D8) else Color(0xFFF5E3C2), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                VIcon(if (noRecords) R.drawable.ic_drop_outline else R.drawable.ic_check, OkColor, 24.dp)
            } else {
                VIcon(R.drawable.ic_clock, AmberColor, 24.dp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            when {
                noRecords -> {
                    Text("欢迎使用糖记", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = OkInk)
                    Spacer(Modifier.height(3.dp))
                    Text("测完血糖，点下面的 ＋ 记第一笔", fontSize = 15.5.sp, color = Color(0xFF3C7A63))
                }
                miss.isEmpty() -> {
                    Text("今天的时段都记完啦", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = OkInk)
                    Spacer(Modifier.height(3.dp))
                    Text("已经连续记录 $streak 天，真棒", fontSize = 15.5.sp, color = Color(0xFF3C7A63))
                }
                else -> {
                    Text("还有 ${miss.size} 个时段没记", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6D4D0F))
                    Spacer(Modifier.height(2.dp))
                    Text("点一下直接补记：", fontSize = 15.5.sp, color = Color(0xFF8A6D33))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        miss.take(6).forEach { p ->
                            QuickPill(p.label) { onQuickRecord(p.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(r: Record, targets: Targets, onClick: () -> Unit) {
    val p = periodById(r.period)
    val st = Stats.statusOf(r.value, p.tgt, targets)
    val (lo, hi) = targets.range(p.tgt)
    Box(Modifier.fillMaxWidth()) {
        CardBox(Modifier.clickable(onClick = onClick)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近一次 · ${Stats.fmtDT(Stats.toLocalDateTime(r.measuredAt))}",
                    fontSize = 15.5.sp, color = SubColor, modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(OkBg)
                        .padding(horizontal = 15.dp, vertical = 7.dp)
                ) {
                    Text(p.label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryDeep)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Stats.fmt1(r.value), fontSize = 72.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp,
                    color = if (st == "ok") Ink else statusColor(st)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "mmol/L", fontSize = 18.sp, color = SubColor, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(st, Stats.statusText(st), showCheck = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${p.label}目标 ${Stats.fmt1(lo)}–${Stats.fmt1(hi)}",
                    fontSize = 15.5.sp, color = SubColor
                )
            }
            if (r.note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("备注：${r.note}", fontSize = 15.5.sp, color = SubColor)
            }
        }
        // 水滴水印（悬浮在卡片右上角，不占布局空间）
        Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp)) {
            VIcon(R.drawable.ic_drop, PrimaryColor.copy(alpha = 0.055f), 104.dp)
        }
    }
}

@Composable
private fun MiniTrendCard(records: List<Record>, targets: Targets, goTrend: () -> Unit) {
    CardBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("近 7 天趋势", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
            Row(
                Modifier.clickable(onClick = goTrend),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("看完整趋势", fontSize = 15.5.sp, color = PrimaryColor, fontWeight = FontWeight.ExtraBold)
                VIcon(R.drawable.ic_chev_r, PrimaryColor, 16.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        val list = Stats.recordsInRange(records, 7, LocalDate.now())
        if (list.isEmpty() || Stats.dailyMeans(list).size < 2) {
            DashedEmpty(null, "再记几天，这里会出现 7 天趋势线", compact = true)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().height(92.dp)) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val geo = remember(list, w, h) { Chart.compute(list, targets, w, h, 7, true) }
                Canvas(Modifier.fillMaxSize()) {
                    geo?.let { renderTrend(it, mini = true) }
                }
            }
            Text(
                "绿底是目标范围 · 每天 1 个点（日平均）",
                fontSize = 14.5.sp, color = SubColor, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

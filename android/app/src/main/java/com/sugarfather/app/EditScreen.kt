package com.sugarfather.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 两步录入页（PRD FR-1；V2.2：支援预选时段 / 新视觉体系） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(editId: String?, presetPeriod: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val existing = editId?.let { id -> Repo.records.firstOrNull { it.id == id } }
    val targets = Repo.targets

    var step by remember { mutableIntStateOf(1) }
    var valStr by remember { mutableStateOf(existing?.let { Stats.fmt1(it.value) } ?: "") }
    var selPeriod by remember {
        mutableStateOf(existing?.period ?: presetPeriod)
    }
    val recommendId: String? = remember {
        if (existing == null && presetPeriod == null) Stats.recommendPeriod(LocalDateTime.now()) else null
    }
    var measuredAt by remember {
        mutableStateOf(existing?.let { Stats.toLocalDateTime(it.measuredAt) } ?: LocalDateTime.now())
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showMore by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    val check = Stats.validateValue(valStr)

    val keyPress: (String) -> Unit = { k ->
        var s = valStr
        when (k) {
            "del" -> s = s.dropLast(1)
            "dot" -> if (!s.contains('.')) s = (if (s.isEmpty()) "0" else s) + "."
            else -> {
                val dot = s.indexOf('.')
                val allowed = if (dot >= 0) s.length - dot <= 1 else s.length < 2
                if (allowed) s += k
            }
        }
        if (s.length > 1 && s[0] == '0' && s[1] != '.') {
            val trimmed = s.trimStart('0')
            s = if (trimmed.isEmpty() || trimmed[0] == '.') "0$trimmed" else trimmed
        }
        valStr = s
    }

    val onSave: () -> Unit = {
        val chk = Stats.validateValue(valStr)
        val sp = selPeriod
        when {
            !chk.ok -> toast(ctx, chk.msg)
            sp == null -> toast(ctx, "请选一个测量时段")
            measuredAt.isAfter(LocalDateTime.now().plusMinutes(1)) -> toast(ctx, "测量时间不能是未来的时间")
            else -> {
                val ms = Stats.toEpochMs(measuredAt)
                if (editId != null) Repo.updateRecord(editId, chk.value, sp, note, ms)
                else Repo.addRecord(chk.value, sp, note, ms)
                toast(ctx, if (editId != null) "改好了 ✓" else "记好了，继续保持 ✓")
                onClose()
            }
        }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopBar(if (editId != null) "修改记录" else "记一笔血糖") { onClose() }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            StepProgress(
                current = step,
                text = if (step == 1) "第 1 步，共 2 步：输入数值" else "第 2 步，共 2 步：选时段后保存"
            )

            if (step == 1) {
                // ---- 第 1 步：大号数字键盘 ----
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF5F8F7))
                        .border(1.5.dp, SoftBorder, RoundedCornerShape(20.dp))
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (valStr.isEmpty()) "0.0" else valStr,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.5).sp,
                        color = if (valStr.isEmpty()) Color(0xFFB7BFC9) else Ink
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("mmol/L · 血糖数值", fontSize = 16.5.sp, color = SubColor, fontWeight = FontWeight.Bold)
                }

                val hintText = when {
                    valStr.isEmpty() -> "用下面的键盘输入血糖数值"
                    !check.ok -> check.msg + "，请重新输入"
                    else -> "输入好了，点「下一步」继续"
                }
                Row(
                    Modifier.padding(top = 16.dp, bottom = 4.dp).heightIn(min = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        valStr.isNotEmpty() && check.ok -> {
                            VIcon(R.drawable.ic_check, OkInk, 19.dp)
                            Spacer(Modifier.width(7.dp))
                        }
                        valStr.isNotEmpty() && !check.ok -> {
                            VIcon(R.drawable.ic_cross, HighInk, 17.dp)
                            Spacer(Modifier.width(7.dp))
                        }
                    }
                    Text(
                        hintText, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            valStr.isEmpty() -> SubColor
                            !check.ok -> HighInk
                            else -> OkInk
                        }
                    )
                }

                val quick = remember { Repo.records.map { it.value }.distinct().reversed().take(3) }
                if (quick.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        quick.forEach { v ->
                            Box(
                                Modifier
                                    .shadow(1.dp, RoundedCornerShape(50))
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                                    .border(1.6.dp, Color(0xFFE3E9EE), RoundedCornerShape(50))
                                    .clickable { valStr = Stats.fmt1(v) }
                                    .padding(horizontal = 22.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    Stats.fmt1(v), fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color(0xFF333B48)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("del", "0", "dot")
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(62.dp)
                                    .shadow(2.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (k == "del" || k == "dot") Color(0xFFEEF1F5) else Color.White)
                                    .border(1.6.dp, Color(0xFFE7ECEF), RoundedCornerShape(16.dp))
                                    .clickable { keyPress(k) },
                                contentAlignment = Alignment.Center
                            ) {
                                when (k) {
                                    "del" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        VIcon(R.drawable.ic_trash, Color(0xFF3C4452), 19.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("删除", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3C4452))
                                    }
                                    "dot" -> Text("·", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                    else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(6.dp))
                BigButton("下一步：选时段", enabled = check.ok) {
                    if (selPeriod == null) {
                        selPeriod = recommendId ?: Stats.recommendPeriod(LocalDateTime.now())
                    }
                    step = 2
                }
            } else {
                // ---- 第 2 步：选时段 + 保存 ----
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEBEFF3))
                        .clickable { step = 1 },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VIcon(R.drawable.ic_chev_l, Color(0xFF333B48), 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("上一步，改数值", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF333B48))
                }
                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF5F8F7))
                        .border(1.5.dp, SoftBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (check.ok) Stats.fmt1(check.value) else if (valStr.isEmpty()) "0.0" else valStr,
                        fontSize = 46.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        color = Ink
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("mmol/L", fontSize = 16.5.sp, color = SubColor, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    val sp = selPeriod
                    if (sp != null && check.ok) {
                        val p = periodById(sp)
                        val pst = Stats.statusOf(check.value, p.tgt, targets)
                        StatusTag(pst, Stats.statusText(pst), showCheck = true)
                    }
                }

                SectionLabel("测量时段（已按时间推荐，点一下就能改）")
                PERIODS.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { p ->
                            val selected = selPeriod == p.id
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(66.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) OkBg else Color.White)
                                    .border(
                                        2.5.dp,
                                        if (selected) PrimaryColor else Color(0xFFE3E9EE),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clickable { selPeriod = p.id },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.label,
                                    fontSize = 19.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (selected) PrimaryDeep else Color(0xFF333B48)
                                )
                                if (selected) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-8).dp)
                                            .size(26.dp)
                                            .shadow(3.dp, CircleShape)
                                            .clip(CircleShape)
                                            .background(PrimaryColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VIcon(R.drawable.ic_check, Color.White, 14.dp)
                                    }
                                }
                                if (recommendId != null && p.id == recommendId) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-11).dp)
                                            .shadow(1.dp, RoundedCornerShape(50))
                                            .clip(RoundedCornerShape(50))
                                            .background(PrimaryColor)
                                            .padding(horizontal = 12.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "推荐", fontSize = 12.5.sp,
                                            color = Color.White, fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(4.dp))
                BigButton("保 存", enabled = selPeriod != null && check.ok) { onSave() }

                // 更多选项
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showMore = !showMore }
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VIcon(R.drawable.ic_chev_d, PrimaryColor, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showMore) "收起更多选项" else "更多选项：修改时间 / 写备注",
                        fontSize = 16.5.sp, color = PrimaryColor, fontWeight = FontWeight.ExtraBold
                    )
                }

                if (showMore) {
                    SectionLabel("测量时间（补记才需要改）")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.6.dp, Color(0xFFE3E9EE), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (measuredAt.toLocalDate() == LocalDate.now()) "今天"
                            else Stats.fmtMD(measuredAt.toLocalDate())) + " " + Stats.fmtHM(measuredAt),
                            fontSize = 17.5.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f)
                        )
                        SmallChip("改日期") { showDatePicker = true }
                        Spacer(Modifier.width(10.dp))
                        SmallChip("改时间") { showTimePicker = true }
                    }
                    SectionLabel("备注（可不填）")
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 200) note = it },
                        singleLine = true,
                        placeholder = { Text("例：吃了两个包子 / 散步 40 分钟", fontSize = 17.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 17.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (editId != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HighBg)
                            .border(2.dp, Color(0xFFF0C7C7), RoundedCornerShape(16.dp))
                            .clickable { showDelete = true },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VIcon(R.drawable.ic_trash, HighInk, 19.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("删除这条记录", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = HighInk)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 日期选择
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = measuredAt.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        measuredAt = d.atTime(measuredAt.toLocalTime())
                        if (measuredAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
                            toast(ctx, "测量时间不能是未来的时间")
                            measuredAt = LocalDateTime.now()
                        }
                    }
                    showDatePicker = false
                }) { Text("确定", fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", fontSize = 18.sp) }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    // 时间选择
    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = measuredAt.hour,
            initialMinute = measuredAt.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    measuredAt = measuredAt.withHour(tpState.hour).withMinute(tpState.minute)
                    if (measuredAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
                        toast(ctx, "测量时间不能是未来的时间")
                        measuredAt = LocalDateTime.now()
                    }
                    showTimePicker = false
                }) { Text("确定", fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消", fontSize = 18.sp) }
            },
            text = { TimePicker(state = tpState) }
        )
    }

    // 删除确认
    if (showDelete && editId != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这条记录？", fontSize = 20.sp) },
            text = { Text("删除后找不回来", fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteRecord(editId)
                    toast(ctx, "已删除")
                    showDelete = false
                    onClose()
                }) { Text("删除", fontSize = 18.sp, color = HighInk) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("先留着", fontSize = 18.sp) }
            }
        )
    }
}

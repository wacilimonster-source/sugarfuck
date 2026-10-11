package com.sugarfather.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 提醒页（V2.2 升级为一级 Tab）：≤2 条每日提醒 + 权限与可靠性引导 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen() {
    val ctx = LocalContext.current

    var list by remember {
        val base = Repo.reminders.toMutableList()
        while (base.size < 2) base.add(Reminder(false, 21, 30, REMINDER_TEXTS[2]))
        mutableStateOf(base.take(2))
    }
    var permGranted by remember { mutableStateOf(Notify.hasPermission(ctx)) }
    var pickerIndex by remember { mutableStateOf<Int?>(null) }
    val exactOk = remember {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ReminderScheduler.canExact(am)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permGranted = granted
        if (granted) toast(ctx, "通知权限已开启")
    }

    fun persist(newList: List<Reminder>) {
        list = newList
        Repo.saveReminders(newList)
    }

    fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !permGranted) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(containerColor = BgColor) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            PageHeader("提醒", "每天定时提醒测量血糖，最多两条")
            Spacer(Modifier.height(6.dp))

            list.forEachIndexed { i, r ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "提醒 ${i + 1}", fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold, color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = r.enabled,
                            onCheckedChange = { on ->
                                val nl = list.toMutableList()
                                nl[i] = r.copy(enabled = on)
                                persist(nl)
                                if (on) requestNotifIfNeeded()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryColor)
                        )
                    }
                    if (r.enabled) {
                        // 大号时间块
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF5F8F7))
                                .border(1.5.dp, SoftBorder, RoundedCornerShape(16.dp))
                                .clickable { pickerIndex = i }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "%02d:%02d".format(r.hour, r.minute),
                                fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryDeep
                            )
                            Spacer(Modifier.weight(1f))
                            Text("点时间可以修改", fontSize = 14.5.sp, color = SubColor)
                        }
                        // 提醒内容模板
                        Text(
                            "提醒内容", fontSize = 15.5.sp, fontWeight = FontWeight.Bold,
                            color = SubColor, modifier = Modifier.padding(top = 14.dp)
                        )
                        REMINDER_TEXTS.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                pair.forEach { t ->
                                    val sel = r.text == t
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (sel) OkBg else Color.White)
                                            .border(
                                                1.6.dp,
                                                if (sel) PrimaryColor else Color(0xFFE3E9EE),
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                val nl = list.toMutableList()
                                                nl[i] = r.copy(text = t)
                                                persist(nl)
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (sel) {
                                            VIcon(R.drawable.ic_check, PrimaryDeep, 15.dp)
                                            Spacer(Modifier.width(5.dp))
                                        }
                                        Text(
                                            t, fontSize = 15.sp,
                                            color = if (sel) PrimaryDeep else Color(0xFF333B48),
                                            fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Bold,
                                            textAlign = TextAlign.Center, maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 推荐组合（次级按钮）
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFE3EFE9))
                    .clickable {
                        persist(
                            listOf(
                                Reminder(true, 7, 0, REMINDER_TEXTS[0]),
                                Reminder(true, 21, 30, REMINDER_TEXTS[2])
                            )
                        )
                        requestNotifIfNeeded()
                        toast(ctx, "已开启两条推荐提醒")
                    }
                    .padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VIcon(R.drawable.ic_star, PrimaryColor, 19.dp)
                Spacer(Modifier.width(8.dp))
                Text("一键套用推荐组合（早 7:00 + 晚 21:30）", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryColor)
            }
            Spacer(Modifier.height(14.dp))

            // 可靠性
            CardBox {
                Text("让提醒更可靠", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (permGranted) OkBg else HighBg),
                        contentAlignment = Alignment.Center
                    ) {
                        VIcon(R.drawable.ic_bell, if (permGranted) OkColor else HighColor, 20.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (permGranted) "通知权限已开启"
                        else "通知权限未开启，提醒不会弹出来",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (permGranted) OkInk else HighInk,
                        modifier = Modifier.weight(1f)
                    )
                    if (!permGranted) {
                        SmallChip("去开启") {
                            if (Build.VERSION.SDK_INT >= 33) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotifSettings(ctx)
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 31 && !exactOk) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(LowBg),
                            contentAlignment = Alignment.Center
                        ) {
                            VIcon(R.drawable.ic_clock, LowColor, 20.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "精确定时未开启，提醒可能晚几分钟",
                            fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = LowInk,
                            modifier = Modifier.weight(1f)
                        )
                        SmallChip("去开启") { openExactSettings(ctx) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(LowBg),
                        contentAlignment = Alignment.Center
                    ) {
                        VIcon(R.drawable.ic_battery, LowColor, 20.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "建议把「糖记」加入电池优化白名单，避免省电延迟提醒",
                        fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = SubColor,
                        modifier = Modifier.weight(1f)
                    )
                    SmallChip("去设置") { openBatterySettings(ctx) }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    pickerIndex?.let { idx ->
        val r = list[idx]
        val tpState = rememberTimePickerState(
            initialHour = r.hour, initialMinute = r.minute, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickerIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    val nl = list.toMutableList()
                    nl[idx] = r.copy(hour = tpState.hour, minute = tpState.minute)
                    persist(nl)
                    pickerIndex = null
                }) { Text("确定", fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pickerIndex = null }) { Text("取消", fontSize = 18.sp) }
            },
            text = { TimePicker(state = tpState) }
        )
    }
}

private fun openExactSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + ctx.packageName)
                )
            )
        }.onFailure { toast(ctx, "打不开系统设置，请手动到设置里开启") }
    }
}

private fun openBatterySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }.onFailure { toast(ctx, "打不开系统设置，请手动到设置里操作") }
}

private fun openNotifSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }.onFailure { toast(ctx, "打不开系统设置") }
}

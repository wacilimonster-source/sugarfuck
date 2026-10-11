package com.sugarfather.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@Composable
fun MineScreen(onUpdate: () -> Unit) {
    val ctx = LocalContext.current
    val targets = Repo.targets
    val scope = rememberCoroutineScope()

    var editorOpen by remember { mutableStateOf(false) }
    var fLo by remember(targets) { mutableStateOf(Stats.fmt1(targets.fastingLo)) }
    var fHi by remember(targets) { mutableStateOf(Stats.fmt1(targets.fastingHi)) }
    var nLo by remember(targets) { mutableStateOf(Stats.fmt1(targets.nonFastingLo)) }
    var nHi by remember(targets) { mutableStateOf(Stats.fmt1(targets.nonFastingHi)) }
    var pendingImport by remember { mutableStateOf<Csv.ImportPreview?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                toast(ctx, "读不到这个文件，请换一个试试")
            } else {
                val preview = Csv.parseCsv(
                    text, Repo.existingIds(), Repo.existingKeys(),
                    genId = { genImportId() }
                )
                when {
                    preview.error != null -> toast(ctx, preview.error)
                    preview.toAdd.isEmpty() && preview.toOverwrite.isEmpty() ->
                        toast(ctx, "没有需要导入的新记录（跳过重复 ${preview.skipped} 条）")
                    else -> pendingImport = preview
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // 账号卡
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF12A077), PrimaryColor))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("糖", fontSize = 27.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("糖友", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VIcon(R.drawable.ic_shield, PrimaryColor, 16.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "数据存本机（无需联网）",
                            fontSize = 15.5.sp, color = PrimaryColor, fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 目标范围
        CardBox(Modifier.padding(vertical = 0.dp)) {
            MenuRow(
                icon = R.drawable.ic_target, iconBg = OkBg, iconTint = PrimaryColor,
                text = "目标范围设置"
            ) { editorOpen = !editorOpen }
            if (editorOpen) {
                Column(Modifier.padding(bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("空腹", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = SubColor, modifier = Modifier.width(78.dp))
                        NumField(fLo, { fLo = it }, Modifier.weight(1f))
                        Text("~", color = SubColor, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        NumField(fHi, { fHi = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("非空腹", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = SubColor, modifier = Modifier.width(78.dp))
                        NumField(nLo, { nLo = it }, Modifier.weight(1f))
                        Text("~", color = SubColor, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        NumField(nHi, { nHi = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF12A077), PrimaryColor)))
                                .clickable {
                                    val a = listOfNotNull(
                                        fLo.toDoubleOrNull(), fHi.toDoubleOrNull(),
                                        nLo.toDoubleOrNull(), nHi.toDoubleOrNull()
                                    )
                                    when {
                                        a.size != 4 || a.any { it < 1 || it > 25 } ->
                                            toast(ctx, "目标值要在 1.0 到 25.0 之间")
                                        a[0] >= a[1] || a[2] >= a[3] ->
                                            toast(ctx, "下限要比上限小")
                                        else -> {
                                            Repo.saveTargets(Targets(a[0], a[1], a[2], a[3]))
                                            editorOpen = false
                                            toast(ctx, "目标范围改好了，颜色和统计已更新")
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("保存", fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(
                            Modifier
                                .weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE3EFE9))
                                .clickable {
                                    Repo.saveTargets(Targets())
                                    toast(ctx, "已恢复默认目标")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("恢复默认", fontSize = 17.sp, color = PrimaryColor, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // 数据管理
        CardBox(Modifier.padding(vertical = 0.dp)) {
            MenuRow(
                icon = R.drawable.ic_upload, iconBg = Color(0xFFE8F0FD), iconTint = Color(0xFF1D4ED8),
                text = "导出数据文件（发微信 / 存档）"
            ) {
                runCatching {
                    val csv = Repo.exportCsvText()
                    val f = File(ctx.cacheDir, "糖记-${LocalDate.now()}.csv")
                    f.writeText(Csv.BOM + csv)
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "分享血糖数据"))
                }.onFailure { toast(ctx, "导出失败：" + it.message) }
            }
            MenuRow(
                icon = R.drawable.ic_download, iconBg = Color(0xFFE8F0FD), iconTint = Color(0xFF1D4ED8),
                text = "导入数据文件（换手机恢复）"
            ) {
                importLauncher.launch(arrayOf("*/*"))
            }
            MenuRow(
                icon = R.drawable.ic_copy, iconBg = OkBg, iconTint = PrimaryColor,
                text = "复制全部数据（看病时给医生）"
            ) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("糖记", Repo.exportPlainText()))
                toast(ctx, "已复制 ${Repo.records.size} 条记录")
            }
        }

        // 关于 / 检查更新
        CardBox(Modifier.padding(vertical = 0.dp)) {
            MenuRow(
                icon = R.drawable.ic_download,
                iconBg = if (UpdateState.available != null) Color(0xFFE8F0FD) else Color(0xFFEEF1F5),
                iconTint = if (UpdateState.available != null) Color(0xFF1D4ED8) else Color(0xFF5A6270),
                text = "检查更新",
                trailing = when {
                    UpdateState.checking -> "检查中…"
                    UpdateState.available != null -> "发现 V${UpdateState.available!!.versionName}"
                    else -> "V${BuildConfig.VERSION_NAME}"
                }
            ) {
                val info = UpdateState.available
                if (info != null) {
                    onUpdate()
                } else {
                    scope.launch {
                        val r = UpdateChecker.check()
                        if (r != null) {
                            toast(ctx, "发现新版本 V${r.versionName}")
                            onUpdate()
                        } else if (UpdateState.error != null) {
                            toast(ctx, UpdateState.error!!)
                        } else {
                            toast(ctx, "已是最新版本 V${BuildConfig.VERSION_NAME}")
                        }
                    }
                }
            }
        }

        Text(
            "本工具只做记录和参考，不能代替医生诊断。\n血糖管理请听医生的话，数值异常请及时就医。",
            fontSize = 13.5.sp, color = Color(0xFF7A8291), lineHeight = 25.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
    }

    // 导入确认
    pendingImport?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("确认导入", fontSize = 20.sp) },
            text = {
                Text(
                    "将导入 ${p.toAdd.size} 条，覆盖 ${p.toOverwrite.size} 条，跳过重复 ${p.skipped} 条。",
                    fontSize = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Repo.importRecords(p)
                    toast(ctx, "导入完成")
                    pendingImport = null
                }) { Text("导入", fontSize = 18.sp, color = PrimaryColor) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消", fontSize = 18.sp) }
            }
        )
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.length <= 5 && s.all { it.isDigit() || it == '.' }) onChange(s) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = LocalTextStyle.current.copy(fontSize = 21.sp),
        modifier = modifier
    )
}

private fun genImportId(): String {
    val t = java.lang.Long.toString(System.currentTimeMillis(), 36)
    val r = Integer.toString((Math.random() * 1_000_000).toInt() + 1, 36)
    return "r$t$r"
}

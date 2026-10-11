package com.sugarfather.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

/** 发现新版本对话框：说明 + 下载进度 + 下载并安装 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = UpdateState.available ?: return
    var downloaded by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = { if (!UpdateState.downloading) onDismiss() },
        title = { Text("发现新版本 V${info.versionName}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    info.notes.ifBlank { "建议更新到最新版本" },
                    fontSize = 16.sp, lineHeight = 26.sp
                )
                if (UpdateState.downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { UpdateState.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("正在下载 ${UpdateState.progress}%", fontSize = 15.sp, color = SubColor)
                }
                UpdateState.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("下载失败：$it", fontSize = 14.sp, color = HighInk, lineHeight = 22.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !UpdateState.downloading,
                onClick = {
                    val f = downloaded
                    if (f != null) {
                        UpdateChecker.install(ctx, f)
                        onDismiss()
                    } else {
                        scope.launch {
                            val file = UpdateChecker.download(ctx)
                            if (file != null) {
                                downloaded = file
                                UpdateChecker.install(ctx, file)
                                onDismiss()
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (UpdateState.downloading) "下载中…" else "下载并安装",
                    fontSize = 18.sp, color = PrimaryColor, fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !UpdateState.downloading, onClick = onDismiss) {
                Text("稍后", fontSize = 18.sp)
            }
        }
    )
}

package com.sugarfather.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 更新信息（来自仓库根的 version.json） */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkUrlMirror: String,
    val notes: String
)

/** 更新状态（Compose 直接观察） */
object UpdateState {
    var checking by mutableStateOf(false)
    var available by mutableStateOf<UpdateInfo?>(null)
    var downloading by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
}

/**
 * 检查更新：读取 GitHub 仓库根目录的 version.json，对比 versionCode。
 * APK 托管在仓库 releases/ 目录（无需 GitHub API、无速率限制）。
 * 如手机访问 GitHub 慢，可在 version.json 里填 apkUrlMirror（如 ghproxy 前缀）作为备用源。
 */
object UpdateChecker {

    // 版本清单来源：① GitHub API（无 CDN 缓存，实时；匿名限流 60 次/时，个人使用足够）
    //               ② raw.githubusercontent（5 分钟 CDN 缓存，作兜底）
    private fun manifestSources(): List<Pair<String, Map<String, String>>> = listOf(
        "https://api.github.com/repos/wacilimonster-source/sugarfuck/contents/version.json"
            to mapOf("Accept" to "application/vnd.github.raw"),
        "https://raw.githubusercontent.com/wacilimonster-source/sugarfuck/main/version.json?t=${System.currentTimeMillis()}"
            to emptyMap()
    )

    /** 返回非空表示有新版本；网络错误返回 null 并写 UpdateState.error */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        UpdateState.checking = true
        UpdateState.error = null
        try {
            var obj: JSONObject? = null
            var lastErr: Exception? = null
            for ((url, headers) in manifestSources()) {
                try {
                    obj = JSONObject(httpGetText(url, headers))
                    break
                } catch (e: Exception) {
                    lastErr = e
                }
            }
            val o = obj ?: throw (lastErr ?: IllegalStateException("无法获取版本信息"))
            val code = o.optInt("versionCode", 0)
            val info = UpdateInfo(
                versionCode = code,
                versionName = o.optString("versionName", ""),
                apkUrl = o.optString("apkUrl", ""),
                apkUrlMirror = o.optString("apkUrlMirror", ""),
                notes = o.optString("notes", "")
            )
            if (code > BuildConfig.VERSION_CODE && info.apkUrl.isNotEmpty()) {
                UpdateState.available = info
                info
            } else {
                UpdateState.available = null
                null
            }
        } catch (e: Exception) {
            UpdateState.error = friendlyError(e)
            null
        } finally {
            UpdateState.checking = false
        }
    }

    /** 下载新版 APK 到应用缓存；失败返回 null */
    suspend fun download(ctx: Context): File? = withContext(Dispatchers.IO) {
        val info = UpdateState.available ?: return@withContext null
        UpdateState.downloading = true
        UpdateState.progress = 0
        UpdateState.error = null
        try {
            val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
            val out = File(dir, "sugarfather-${info.versionName}.apk")
            if (out.exists()) out.delete()
            val sources = listOfNotNull(
                info.apkUrl.takeIf { it.isNotBlank() },
                info.apkUrlMirror.takeIf { it.isNotBlank() }
            )
            for (u in sources) {
                try {
                    downloadTo(u, out) { p -> UpdateState.progress = p }
                    if (out.length() > 100_000) return@withContext out
                } catch (e: Exception) {
                    UpdateState.error = friendlyError(e)
                }
            }
            null
        } finally {
            UpdateState.downloading = false
        }
    }

    /** 调起系统安装器 */
    fun install(ctx: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        }.onFailure { toast(ctx, "打不开安装器，请到文件管理器里手动安装") }
    }

    private fun friendlyError(e: Exception): String = when {
        e is java.net.UnknownHostException -> "连不上更新服务器（网络或 GitHub 访问受限）"
        e is java.net.SocketTimeoutException -> "连接超时，请稍后重试"
        else -> e.message ?: "网络错误"
    }

    private fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SugarFather-Updater")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun downloadTo(url: String, out: File, onProgress: (Int) -> Unit) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SugarFather-Updater")
        }
        try {
            val total = c.contentLength.toLong()
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var sum = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        sum += read
                        if (total > 0) onProgress(((sum * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}

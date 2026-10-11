package com.sugarfather.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* 适老化加深色板（PRD 五：正文对比度 ≥7:1，状态色加深版） */
val Ink = Color(0xFF191D23)
val SubColor = Color(0xFF49505E)
val BgColor = Color(0xFFF2F4F6)
val PrimaryColor = Color(0xFF0B7D5E)
val PrimaryDeep = Color(0xFF085C45)
val OkColor = Color(0xFF0E9C73)
val OkInk = Color(0xFF0A5C44)
val OkBg = Color(0xFFDFF4EA)
val LowColor = Color(0xFFB45309)
val LowInk = Color(0xFF8A4106)
val LowBg = Color(0xFFFDF0DC)
val HighColor = Color(0xFFC53030)
val HighInk = Color(0xFFA02020)
val HighBg = Color(0xFFFDEAEA)
val LineColor = Color(0xFFE3E7EC)
val SoftBg = Color(0xFFF6F8FA)

/* V2.2 设计令牌补充 */
val AmberColor = Color(0xFFE08A00)
val ChevGray = Color(0xFFB9C2CE)
val DashColor = Color(0xFFD2DAE3)
val SoftBorder = Color(0xFFE7EEEA)

fun statusColor(st: String): Color = when (st) {
    "ok" -> OkColor
    "low" -> LowColor
    else -> HighColor
}

fun statusInk(st: String): Color = when (st) {
    "ok" -> OkInk
    "low" -> LowInk
    else -> HighInk
}

fun statusBg(st: String): Color = when (st) {
    "ok" -> OkBg
    "low" -> LowBg
    else -> HighBg
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryColor,
            onPrimary = Color.White,
            secondary = PrimaryDeep,
            background = BgColor,
            surface = Color.White,
            onBackground = Ink,
            onSurface = Ink,
            outline = LineColor
        ),
        content = content
    )
}

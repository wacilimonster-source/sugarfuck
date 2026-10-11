package com.sugarfather.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** 统一矢量图标（V2.2 设计稿：全部线性图标，无 emoji） */
@Composable
fun VIcon(@DrawableRes id: Int, tint: Color, size: Dp = 24.dp) {
    Icon(
        painter = painterResource(id = id),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

/** 白色圆角卡片：统一 20dp 圆角 + 柔和投影 */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content
    )
}

/** 状态胶囊：颜色 + 文字双编码，可选前置 ✓ 图标 */
@Composable
fun StatusTag(
    st: String,
    text: String,
    modifier: Modifier = Modifier,
    showCheck: Boolean = false
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(statusBg(st))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheck) {
            VIcon(R.drawable.ic_check, statusInk(st), 15.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = statusInk(st))
    }
}

/** 记录行：状态圆点带柔光圈 */
@Composable
fun RecordRow(r: Record, targets: Targets, onClick: () -> Unit) {
    val p = periodById(r.period)
    val st = Stats.statusOf(r.value, p.tgt, targets)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(19.dp).background(statusColor(st).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(11.dp).background(statusColor(st), CircleShape))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    if (r.note.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· ${r.note}", fontSize = 15.5.sp, color = SubColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "${Stats.fmtHM(Stats.toLocalDateTime(r.measuredAt))} · ${Stats.statusText(st)}",
                    fontSize = 15.5.sp, color = SubColor
                )
            }
            Text(
                String.format(Locale.US, "%.1f", r.value),
                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = if (st == "ok") Ink else statusColor(st)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color(0xFFF2F5F8)))
    }
}

/** 虚线空态：可选圆形图标 + 双行引导 */
@Composable
fun DashedEmpty(
    @DrawableRes icon: Int?,
    line1: String,
    line2: String? = null,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val strokeW = 1.6.dp
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                val w = strokeW.toPx()
                drawRoundRect(
                    color = DashColor,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(16.dp.toPx() - w / 2),
                    style = Stroke(
                        width = w,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
                    )
                )
            }
            .padding(vertical = if (compact) 22.dp else 26.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Box(
                    Modifier.size(56.dp).background(OkBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    VIcon(icon, PrimaryColor, 26.dp)
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(line1, fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333B48))
            if (line2 != null) {
                Spacer(Modifier.height(4.dp))
                Text(line2, fontSize = 15.5.sp, color = SubColor)
            }
        }
    }
}

/** 页面大标题（今天页自绘日期，提醒页用此） */
@Composable
fun PageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        if (subtitle != null) {
            Text(subtitle, fontSize = 15.5.sp, color = SubColor, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 两步进度条 */
@Composable
fun StepProgress(current: Int, text: String, total: Int = 2) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            Box(
                Modifier
                    .width(26.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= current) PrimaryColor else Color(0xFFDCE2E8))
            )
            if (i < total) Spacer(Modifier.width(5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = SubColor)
    }
}

/** 页面顶部栏（标题 + 关闭） */
@Composable
fun TopBar(title: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BgColor)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFEBEFF3))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            VIcon(R.drawable.ic_cross, SubColor, 18.dp)
        }
    }
}

/** 大按钮（主：渐变 + 投影；次：浅灰） */
@Composable
fun BigButton(
    text: String,
    enabled: Boolean = true,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    val brush = when {
        !primary -> Brush.linearGradient(listOf(Color(0xFFEBEFF3), Color(0xFFEBEFF3)))
        enabled -> Brush.linearGradient(listOf(Color(0xFF12A077), PrimaryColor))
        else -> Brush.linearGradient(listOf(Color(0xFFC6D0CB), Color(0xFFC6D0CB)))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .shadow(if (primary && enabled) 8.dp else 0.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
            color = if (primary) Color.White else Color(0xFF333B48)
        )
    }
}

/** 一行菜单（彩色图标底 + 矢量箭头） */
@Composable
fun MenuRow(
    @DrawableRes icon: Int,
    iconBg: Color,
    iconTint: Color,
    text: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            VIcon(icon, iconTint, 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, fontSize = 15.5.sp, color = SubColor, fontWeight = FontWeight.Bold)
        } else {
            VIcon(R.drawable.ic_chev_r, ChevGray, 18.dp)
        }
    }
}

/** 小圆角按钮（淡绿底） */
@Composable
fun SmallChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE3EFE9))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
    }
}

/** 次级区域标题 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text, fontSize = 15.5.sp, color = SubColor, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
    )
}

/** 一键补记胶囊（提醒卡内：点一下直接进入录入并预选该时段） */
@Composable
fun QuickPill(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(1.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VIcon(R.drawable.ic_plus, AmberColor, 15.dp)
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6D4D0F))
    }
}

/** 3 秒长提示（适老） */
fun toast(ctx: android.content.Context, msg: String) {
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
}

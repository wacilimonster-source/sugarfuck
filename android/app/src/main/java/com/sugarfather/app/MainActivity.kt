package com.sugarfather.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        Notify.ensureChannel(this)
        setContent {
            AppTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var presetPeriod by rememberSaveable { mutableStateOf<String?>(null) }
    var updateDialog by rememberSaveable { mutableStateOf(false) }

    // 启动时静默检查更新（失败不打扰）
    LaunchedEffect(Unit) { UpdateChecker.check() }

    if (editOpen) {
        EditScreen(
            editId = editId,
            presetPeriod = presetPeriod,
            onClose = { editOpen = false; editId = null; presetPeriod = null }
        )
    } else {
        MainTabs(
            tab = tab,
            onTab = { tab = it },
            onAdd = { editId = null; presetPeriod = null; editOpen = true },
            onEdit = { id -> editId = id; presetPeriod = null; editOpen = true },
            onQuickRecord = { pid -> editId = null; presetPeriod = pid; editOpen = true },
            onUpdate = { updateDialog = true }
        )
    }

    if (updateDialog) UpdateDialog(onDismiss = { updateDialog = false })
}

@Composable
fun MainTabs(
    tab: Int,
    onTab: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onQuickRecord: (String) -> Unit,
    onUpdate: () -> Unit
) {
    BackHandler(enabled = tab != 0) { onTab(0) }
    Scaffold(
        containerColor = BgColor,
        bottomBar = { BottomBar(tab, onTab, onAdd) }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TodayScreen(
                    onEdit = onEdit,
                    goTrend = { onTab(1) },
                    onQuickRecord = onQuickRecord,
                    onUpdate = onUpdate
                )
                1 -> TrendScreen(onEdit = onEdit)
                2 -> ReminderScreen()
                else -> MineScreen(onUpdate = onUpdate)
            }
        }
    }
}

/** V2.2 五槽对称底栏：[今天][趋势][＋记录][提醒][我的]，＋数学居中
 *  结构：容器 = 32dp 透明露出区 + 白条（84dp+导航栏内边距）
 *  FAB 完全位于容器内（不做任何越界绘制），避免被 Surface 裁剪 */
@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 32.dp),
            color = Color.White,
            shadowElevation = 14.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(84.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTab(R.drawable.ic_home, "今天", tab == 0, Modifier.weight(1f)) { onTab(0) }
                BottomTab(R.drawable.ic_trend, "趋势", tab == 1, Modifier.weight(1f)) { onTab(1) }
                // 中槽：FAB 由外层容器负责绘制，这里只放"记录"标签
                Box(Modifier.width(96.dp).fillMaxHeight()) {
                    Text(
                        "记录",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryColor,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    )
                }
                BottomTab(R.drawable.ic_bell, "提醒", tab == 2, Modifier.weight(1f)) { onTab(2) }
                BottomTab(R.drawable.ic_person, "我的", tab == 3, Modifier.weight(1f)) { onTab(3) }
            }
        }
        // 凸起 FAB：贴容器顶端绘制（圆正好一半在白条上方）
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(64.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF12A077), PrimaryColor)))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            VIcon(R.drawable.ic_plus, Color.White, 30.dp)
        }
    }
}

@Composable
private fun BottomTab(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VIcon(icon, if (selected) PrimaryColor else Color(0xFF828B99), 25.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 14.5.sp,
            color = if (selected) PrimaryColor else Color(0xFF828B99),
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

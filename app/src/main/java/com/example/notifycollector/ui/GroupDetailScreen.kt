package com.example.notifycollector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.data.NotificationEntity
import com.example.notifycollector.util.parseParcel
import com.example.notifycollector.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(nav: NavHostController, groupId: Long) {
    val vm: MainViewModel = viewModel()
    val items by vm.notificationsForGroup(groupId).collectAsStateWithLifecycle(emptyList())
    val group by remember(groupId) { vm.groupFlow(groupId) }
        .collectAsStateWithLifecycle(null)

    // 待二次确认的危险操作
    var pendingDeleteOne by remember { mutableStateOf<NotificationEntity?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }

    // 有效期类分组（如验证码 5 分钟）需要随时间自动置灰，故做 30s 心跳
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // 二次确认：删一条
    pendingDeleteOne?.let { n ->
        ConfirmActionDialog(
            title = "删除此条通知？",
            message = "该通知将被永久删除，无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                vm.deleteNotification(n)
                pendingDeleteOne = null
            },
            onDismiss = { pendingDeleteOne = null }
        )
    }

    // 二次确认：清空整组（按钮已移到顶栏右侧）
    if (pendingClearAll) {
        ConfirmActionDialog(
            title = "清空本组全部通知？",
            message = "本组当前共 ${items.size} 条通知将被全部删除，分组本身保留。该操作无法恢复。",
            confirmLabel = "全部清空",
            onConfirm = {
                vm.clearGroup(groupId)
                pendingClearAll = false
            },
            onDismiss = { pendingClearAll = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "分组通知") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 清空本组：移出每条的右滑菜单，放在顶栏右侧（返回图标 + 分组名这一行）
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { pendingClearAll = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "清空本组",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("暂无命中通知")
                    Text(
                        "符合该分组规则的通知会自动出现在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                // 用 id 作为 key，保证"已读"后重排序时每行的滑动状态跟随正确的数据
                items(items, key = { it.id }) { n ->
                    val expired = group?.isExpired(n.postTime, now) ?: false
                    val dim = n.read || expired
                    SwipeRevealRow(
                        onTap = { nav.navigate("detail/${n.id}") },
                        actions = listOf(
                            SwipeAction(
                                label = if (n.read) "恢复" else "已读",
                                color = if (n.read) Color(0xFFFFB300) /* 参考图黄 */ else Color(0xFF2196F3) /* 蓝 */
                            ) { vm.markRead(n) },
                            SwipeAction(
                                "删除",
                                color = Color(0xFFE53935)
                            ) { pendingDeleteOne = n }
                        )
                    ) {
                        if (group?.cardView == true) {
                            // 取件码等卡片视图分组：三行卡片（取件码 / 快递公司 / 地点）
                            ParcelCard(n, dim)
                        } else {
                            ListItem(
                                headlineContent = { Text(n.title.ifBlank { n.appName }) },
                                supportingContent = {
                                    Text((n.text.ifBlank { "（无内容）" }).take(80), maxLines = 1)
                                },
                                trailingContent = {
                                    when {
                                        n.read -> Text(
                                            "已读",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        expired -> Text(
                                            "已过期",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        else -> Text(
                                            fmt(n.postTime),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (dim) 0.5f else 1f)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * 取件码卡片：第一行大号加粗取件码，第二行快递公司名，第三行快递站地点。
 * 解析失败的字段用兜底文案/正文填充，保证卡片始终可读。
 */
@Composable
private fun ParcelCard(n: NotificationEntity, dim: Boolean) {
    val p = parseParcel(n)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.5f else 1f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p.code ?: "（无取件码）",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fmt(n.postTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                p.company ?: "（未知快递）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                p.location ?: n.text.take(40),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

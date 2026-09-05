package com.example.notifycollector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.data.NotificationEntity
import com.example.notifycollector.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(nav: NavHostController) {
    val vm: MainViewModel = viewModel()
    val items by vm.recycledAll().collectAsStateWithLifecycle(emptyList())
    val groups by vm.groupsWithCount().collectAsStateWithLifecycle(emptyList())
    val nameMap = remember(groups) { groups.associate { it.group.id to it.group.name } }

    // 进入回收站即触发一次自动回收，确保 >14 天的通知已搬入
    LaunchedEffect(Unit) { vm.sweepRecycleBin() }

    // 待二次确认的危险操作
    var pendingDeleteOne by remember { mutableStateOf<NotificationEntity?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }

    pendingDeleteOne?.let { n ->
        ConfirmActionDialog(
            title = "彻底删除此条通知？",
            message = "该通知将被永久删除，无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                vm.deleteRecycled(n)
                pendingDeleteOne = null
            },
            onDismiss = { pendingDeleteOne = null }
        )
    }

    if (pendingClearAll) {
        ConfirmActionDialog(
            title = "清空回收站？",
            message = "回收站内共 ${items.size} 条通知将被全部永久删除，无法恢复。",
            confirmLabel = "清空回收站",
            onConfirm = {
                vm.clearRecycled()
                pendingClearAll = false
            },
            onDismiss = { pendingClearAll = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (items.isEmpty()) "回收站" else "回收站（${items.size}）") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { pendingClearAll = true }) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = "清空回收站",
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
                    Text("回收站是空的")
                    Text(
                        "超过 ${vm.recycleDays} 天的通知会自动移入这里，可恢复或彻底删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(items, key = { it.id }) { n ->
                    SwipeRevealRow(
                        onTap = { nav.navigate("detail/${n.id}") },
                        actions = listOf(
                            SwipeAction(
                                "恢复",
                                color = Color(0xFF2196F3)
                            ) { vm.restoreRecycled(n.id) },
                            SwipeAction(
                                "删除",
                                color = Color(0xFFE53935)
                            ) { pendingDeleteOne = n }
                        )
                    ) {
                        ListItem(
                            headlineContent = { Text(n.title.ifBlank { n.appName }) },
                            supportingContent = {
                                val gName = nameMap[n.groupId] ?: "未知分组"
                                val moved = n.recycledAt?.let { fmt(it) } ?: ""
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        (n.text.ifBlank { "（无内容）" }).take(80),
                                        maxLines = 1
                                    )
                                    Text(
                                        "分组：$gName · 移入 $moved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

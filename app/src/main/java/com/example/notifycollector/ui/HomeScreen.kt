package com.example.notifycollector.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.data.GroupEntity
import com.example.notifycollector.data.GroupWithCount
import com.example.notifycollector.viewmodel.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val vm: MainViewModel = viewModel()
    val groups by vm.groupsWithCount().collectAsStateWithLifecycle(emptyList())
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 必须在 ON_RESUME 时重新检测：用户去系统设置页授权后返回，
    // 若沿用 remember 缓存的旧值，"启用通知监听权限"按钮不会消失。
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingDeleteGroup by remember { mutableStateOf<GroupEntity?>(null) }

    // —— 首页列表拖拽排序模式 ——
    var reorderMode by remember { mutableStateOf(false) }
    var editOrder by remember { mutableStateOf<List<GroupWithCount>>(emptyList()) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()

    fun enterReorder() {
        editOrder = groups
        reorderMode = true
    }
    fun saveReorder() {
        vm.saveGroupOrder(editOrder.map { it.group.id })
        reorderMode = false
        draggedId = null
        dragOffset = 0f
    }

    // 实际展示的列表：排序模式下用本地快照 editOrder，否则用数据库流 groups
    val displayList = if (reorderMode) editOrder else groups

    // 二次确认：删除分组
    pendingDeleteGroup?.let { g ->
        val notifCount = groups.find { it.group.id == g.id }?.notifCount ?: 0
        ConfirmActionDialog(
            title = "删除分组「${g.name}」？",
            message = "该分组及其内 $notifCount 条通知将被永久删除，无法恢复。",
            confirmLabel = "删除分组",
            onConfirm = {
                vm.deleteGroup(g)
                pendingDeleteGroup = null
            },
            onDismiss = { pendingDeleteGroup = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知收集器") },
                actions = {
                    if (reorderMode) {
                        TextButton(onClick = { saveReorder() }) { Text("保存") }
                    } else {
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("add") }) {
                Icon(Icons.Default.Add, contentDescription = "新建分组")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            if (!listenerEnabled) {
                Button(
                    onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Text("启用通知监听权限")
                }
            }

            if (reorderMode) {
                Text(
                    "拖动右侧横杠排序，点「保存」生效",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("还没有分组")
                        Text(
                            "点击右下角 + 新建分组；也可以在【设置 - 模板】中添加常用模板",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(state = listState) {
                    itemsIndexed(displayList, key = { _, it -> it.group.id }) { index, gwc ->
                        val g = gwc.group
                        if (reorderMode) {
                            ReorderRow(
                                gwc = gwc,
                                index = index,
                                isDragging = draggedId == g.id,
                                isShaking = reorderMode,
                                dragOffset = if (draggedId == g.id) dragOffset else 0f,
                                onDragStart = { draggedId = g.id; dragOffset = 0f },
                                onDrag = { delta ->
                                    dragOffset += delta
                                    val list = editOrder
                                    val idx = list.indexOfFirst { it.group.id == g.id }
                                    if (idx < 0) return@ReorderRow
                                    val itemH = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == g.id }?.size ?: 0
                                    if (itemH <= 0) return@ReorderRow
                                    val steps = (dragOffset / itemH).toInt()
                                    val target = (idx + steps).coerceIn(0, list.size - 1)
                                    if (target != idx) {
                                        val swapped = list.toMutableList()
                                            .apply { add(target, removeAt(idx)) }
                                        editOrder = swapped
                                        dragOffset = 0f
                                    }
                                },
                                onDragEnd = { draggedId = null; dragOffset = 0f }
                            )
                            HorizontalDivider()
                        } else {
                            SwipeRevealRow(
                                onTap = { nav.navigate("group/${g.id}") },
                                onLongPress = { enterReorder() },
                                actions = listOf(
                                    SwipeAction(
                                        "编辑",
                                        color = Color(0xFF2196F3)
                                    ) { nav.navigate("edit/${g.id}") },
                                    SwipeAction(
                                        "删除",
                                        color = Color(0xFFE53935)
                                    ) { pendingDeleteGroup = g }
                                )
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            g.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = {
                                        val ttl = if (g.expireMinutes > 0) "有效期 ${g.expireMinutes} 分钟" else "长期有效"
                                        Text(
                                            ttl,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = {
                                        Text(
                                            gwc.unreadCount.toString(),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 排序模式下的单行：显示拖拽手柄（四个横杠）。进入排序模式后所有行一起上下摆动，
 * 提示「处于编辑态」（类似 iOS 桌面编辑时的抖动）。每行带轻微相位差，看起来更自然。
 * 仅手柄区域可触发纵向拖拽（[onDrag]），由调用方在 [dragOffset] 累积并交换列表顺序。
 */
@Composable
private fun ReorderRow(
    gwc: GroupWithCount,
    index: Int,
    isDragging: Boolean,
    isShaking: Boolean,
    dragOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    // 进入排序模式后，所有行统一上下摆动（±3dp），并带基于行号的相位差（相邻行错开半周期），
    // 避免整列像一块板子同时平移。频率 280ms，柔和。
    val phase = (index % 2) * 140
    val wiggle by rememberInfiniteTransition().animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            tween(280, delayMillis = phase),
            RepeatMode.Reverse
        )
    )
    val ttl = if (gwc.group.expireMinutes > 0) "有效期 ${gwc.group.expireMinutes} 分钟" else "长期有效"
    Box(
        Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(
                    0,
                    if (isDragging) dragOffset.roundToInt()
                    else if (isShaking) wiggle.roundToInt()
                    else 0
                )
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    gwc.group.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    ttl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                gwc.unreadCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            // 拖拽手柄：四个横杠图标
            Box(
                Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() }
                        )
                    }
                    .padding(12.dp)
            ) {
                DragHandleIcon()
            }
        }
    }
}

/** 四个横杠的拖拽手柄图标 */
@Composable
private fun DragHandleIcon() {
    Column(
        Modifier.width(18.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

fun isListenerEnabled(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(
        ctx.contentResolver,
        "enabled_notification_listeners"
    )
    return enabled?.contains(ctx.packageName) == true
}

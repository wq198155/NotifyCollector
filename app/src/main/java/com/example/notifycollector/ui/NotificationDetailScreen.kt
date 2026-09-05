package com.example.notifycollector.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(nav: NavHostController, notifId: Long) {
    val vm: MainViewModel = viewModel()
    val n by vm.notificationById(notifId).collectAsStateWithLifecycle(null)
    val ctx = LocalContext.current

    // 该通知所属分组，用于判断是否已过有效期
    val groupId = n?.groupId ?: -1L
    val group by remember(groupId) { vm.groupFlow(groupId) }
        .collectAsStateWithLifecycle(null)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val expired = n?.let { group?.isExpired(it.postTime, now) } ?: false

    // 删除前二次确认：点垃圾桶后先弹确认框，再真正删除
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && n != null) {
        ConfirmActionDialog(
            title = "删除此条通知？",
            message = "该通知将被永久删除，无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                vm.deleteNotification(n!!)
                showDeleteConfirm = false
                nav.popBackStack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    fun copy(text: String, label: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "已复制：$text", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知详情") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    n?.let { item ->
                        IconButton(onClick = { copy(item.extractedCode ?: item.text, "通知内容") }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        n?.let { item ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 验证码 / 取件码卡片 + 一键复制（过期则置灰）
                if (!item.extractedCode.isNullOrBlank()) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (expired) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "提取到的代码",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.width(8.dp))
                                if (expired) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("已过期") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                }
                            }
                            Text(
                                item.extractedCode!!,
                                style = MaterialTheme.typography.displaySmall,
                                color = if (expired) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                            if (expired) {
                                Text(
                                    "该代码已超过有效期（${group?.expireMinutes ?: 0} 分钟），可能已失效",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.Button(
                                onClick = { copy(item.extractedCode!!, "验证码") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("一键复制")
                            }
                        }
                    }
                }

                Text("来自：${item.appName}（${item.packageName}）")
                Text("时间：${fmt(item.postTime)}")

                Spacer(Modifier.height(4.dp))
                Text("标题", style = MaterialTheme.typography.labelMedium)
                Text(item.title.ifBlank { "（无）" })

                Spacer(Modifier.height(4.dp))
                Text("内容", style = MaterialTheme.typography.labelMedium)
                Text(item.text.ifBlank { "（无）" })
            }
        } ?: Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) { Text("加载中…") }
    }
}

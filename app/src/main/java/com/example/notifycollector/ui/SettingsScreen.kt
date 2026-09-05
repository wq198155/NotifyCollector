package com.example.notifycollector.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.util.RuleBackup
import com.example.notifycollector.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm: MainViewModel = viewModel()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var days by remember { mutableStateOf(vm.recycleDays) }
    var showAbout by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // —— 导出备份：SAF 选个目标文件，把全部分组打包成 zip ——
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val groups = vm.getAllGroups()
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    RuleBackup.writeZip(os, groups)
                }
                groups.size
            }.onSuccess { n ->
                Toast.makeText(ctx, "已导出 $n 个分组", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // —— 导入备份：SAF 选个 zip，读出分组后弹二次确认再替换 ——
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportUri = uri
    }

    pendingImportUri?.let { uri ->
        ConfirmActionDialog(
            title = "恢复备份？",
            message = "将用备份替换全部分组设置，并清空已收集的通知，确定吗？",
            confirmLabel = "恢复备份",
            onConfirm = {
                scope.launch {
                    runCatching {
                        val groups = ctx.contentResolver.openInputStream(uri)
                            ?.use { RuleBackup.readZip(it) } ?: emptyList()
                        vm.replaceAllGroups(groups)
                        groups.size
                    }.onSuccess { n ->
                        Toast.makeText(ctx, "已恢复 $n 个分组", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(ctx, "恢复失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("应用：通知接收器")
                    Text("版本：1.0")
                    Text("开发者：smartWQ")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("确定") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            // —— 常规 ——
            SectionHeader("常规")
            ListItem(
                headlineContent = { Text("历史保留") },
                supportingContent = { Text("超过该天数的通知自动进入回收站") },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (days > com.example.notifycollector.data.SettingsStore.MIN_RECYCLE_DAYS) {
                                    days--
                                    vm.updateRecycleDays(days)
                                }
                            }
                        ) { Icon(Icons.Filled.Remove, contentDescription = "减少") }
                        Text(
                            "$days 天",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                if (days < com.example.notifycollector.data.SettingsStore.MAX_RECYCLE_DAYS) {
                                    days++
                                    vm.updateRecycleDays(days)
                                }
                            }
                        ) { Icon(Icons.Filled.Add, contentDescription = "增加") }
                    }
                }
            )
            HorizontalDivider()

            // —— 模板 ——
            SectionHeader("模板")
            ListItem(
                headlineContent = { Text("常用分组模板") },
                supportingContent = { Text("一键添加常见场景的预置分组规则") },
                trailingContent = {
                    TrailingIcon(
                        Icons.Filled.Apps,
                        contentDescription = "常用分组模板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate("presets") }
            )
            HorizontalDivider()

            // —— 导出 / 导入规则 ——
            SectionHeader("导出 / 导入规则")
            ListItem(
                headlineContent = { Text("导出备份") },
                supportingContent = { Text("把当前分组设置打包为 zip 文件") },
                trailingContent = {
                    TrailingIcon(
                        Icons.Filled.FileUpload,
                        contentDescription = "导出备份",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportLauncher.launch("notifycollector_rules_${dateStamp()}.zip") }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("导入备份") },
                supportingContent = { Text("从备份包恢复全部分组设置") },
                trailingContent = {
                    TrailingIcon(
                        Icons.Filled.FileDownload,
                        contentDescription = "导入备份",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { importLauncher.launch(arrayOf("application/zip")) }
            )
            HorizontalDivider()

            // —— 关于 ——
            SectionHeader("关于")
            ListItem(
                headlineContent = { Text("关于") },
                trailingContent = {
                    TrailingIcon(
                        Icons.Filled.Info,
                        contentDescription = "关于",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAbout = true }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun dateStamp(): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

// 尾部图标统一包进 48dp 盒子，使图标中心与 IconButton（第一行 +/-）对齐
@Composable
private fun TrailingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color = LocalContentColor.current
) {
    Box(
        Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector, contentDescription = contentDescription, tint = tint)
    }
}

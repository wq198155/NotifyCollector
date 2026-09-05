package com.example.notifycollector.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.data.MatchType
import com.example.notifycollector.viewmodel.MainViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * 新建 / 编辑分组。
 * groupId 为 null 时是新建，否则进入编辑模式并预填已有规则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupScreen(nav: NavHostController, groupId: Long? = null) {
    val vm: MainViewModel = viewModel()
    val isEdit = groupId != null

    val existingFlow = remember(groupId) {
        if (groupId != null) vm.groupFlow(groupId) else flowOf(null)
    }
    val existing by existingFlow.collectAsStateWithLifecycle(null)

    var name by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf("") }
    var codePattern by remember { mutableStateOf("(?:验证码|动态密码|code)[^0-9]{0,12}?([0-9]{4,8})") }
    var expireText by remember { mutableStateOf("0") }
    var cardView by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    // 编辑模式：已有分组数据到达后回填一次
    LaunchedEffect(existing) {
        val g = existing ?: return@LaunchedEffect
        if (!initialized) {
            name = g.name
            isRegex = g.matchType == MatchType.REGEX
            pattern = g.pattern
            codePattern = g.codePattern
            expireText = g.expireMinutes.toString()
            cardView = g.cardView
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑分组" else "新建分组") },
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
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称（如：验证码）") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("匹配方式：")
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !isRegex,
                    onClick = { isRegex = false },
                    label = { Text("关键字") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = isRegex,
                    onClick = { isRegex = true },
                    label = { Text("正则表达式") }
                )
            }

            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text(if (isRegex) "正则表达式" else "关键字（包含即命中）") },
                placeholder = {
                    Text(if (isRegex) "如 验证码|动态密码|verification code" else "如 验证码")
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = codePattern,
                onValueChange = { codePattern = it },
                label = { Text("提取码正则（可选，用于卡片展示）") },
                placeholder = { Text("如 (?:验证码|code)[^0-9]{0,12}?([0-9]{4,8})") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = expireText,
                onValueChange = { expireText = it },
                label = { Text("有效期（分钟，0 表示不过期）") },
                placeholder = { Text("如验证码填 5，到期自动置灰") },
                modifier = Modifier.fillMaxWidth()
            )

            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedPattern = pattern.trim()
                    val minutes = expireText.trim().toIntOrNull()

                    error = when {
                        trimmedName.isBlank() -> "请填写分组名称"
                        trimmedPattern.isBlank() -> "请填写表达式"
                        isRegex && runCatching { Regex(trimmedPattern) }.isFailure -> "正则表达式无效"
                        minutes == null -> "有效期必须是整数分钟"
                        minutes < 0 -> "有效期不能为负数"
                        else -> ""
                    }
                    if (error.isEmpty()) {
                        val matchType = if (isRegex) MatchType.REGEX else MatchType.KEYWORD
                        if (isEdit && existing != null) {
                            vm.updateGroup(
                                existing!!.copy(
                                    name = trimmedName,
                                    matchType = matchType,
                                    pattern = trimmedPattern,
                                    codePattern = codePattern.trim(),
                                    expireMinutes = minutes ?: 0,
                                    cardView = cardView
                                )
                            )
                        } else {
                            vm.addGroup(
                                name = trimmedName,
                                matchType = matchType,
                                pattern = trimmedPattern,
                                codePattern = codePattern.trim(),
                                expireMinutes = minutes ?: 0,
                                cardView = cardView
                            )
                        }
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEdit) "保存修改" else "保存分组")
            }

            if (isEdit) {
                var showDeleteConfirm by remember { mutableStateOf(false) }

                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("删除此分组（含组内通知）", color = MaterialTheme.colorScheme.error)
                }

                if (showDeleteConfirm && existing != null) {
                    ConfirmActionDialog(
                        title = "删除分组「${existing!!.name}」？",
                        message = "该分组及其内全部通知将被永久删除，无法恢复。",
                        confirmLabel = "删除分组",
                        onConfirm = {
                            vm.deleteGroup(existing!!)
                            showDeleteConfirm = false
                            nav.popBackStack()
                        },
                        onDismiss = { showDeleteConfirm = false }
                    )
                }

                Text(
                    "提示：修改规则只影响之后收到的通知，已收集的不会重新归类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

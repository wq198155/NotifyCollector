package com.example.notifycollector.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.notifycollector.data.GroupPresets
import com.example.notifycollector.data.MatchType
import com.example.notifycollector.viewmodel.MainViewModel

/**
 * 常用分组模板：一键创建，免手输正则。
 * 同名分组已存在时提示"已存在"，不重复创建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(nav: NavHostController) {
    val vm: MainViewModel = viewModel()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("常用分组模板") },
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
            Text(
                "这些是常见场景的预置规则，点「添加」即可使用，之后可在分组里查看效果。",
                style = MaterialTheme.typography.bodySmall
            )

            GroupPresets.all.forEach { p ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                vm.addPreset(p) { ok ->
                                    Toast.makeText(
                                        ctx,
                                        if (ok) "已添加分组：${p.name}" else "「${p.name}」已存在",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }) {
                                Text("添加")
                            }
                        }
                        Text(
                            p.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (p.matchType == MatchType.REGEX) "正则：${p.pattern}" else "关键字：${p.pattern}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        if (p.expireMinutes > 0) {
                            Text(
                                "有效期 ${p.expireMinutes} 分钟（到期置灰）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

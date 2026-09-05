package com.example.notifycollector.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 通用"二次确认"对话框。所有删除按钮都先弹此框再真正执行破坏性操作。
 *
 * @param title 标题，如"删除此条通知？"
 * @param message 详细说明（如要删多少条、不可恢复等）
 * @param confirmLabel 确认按钮文案，默认"确认"
 * @param destructive 是否为破坏性操作；是则按钮用 error 色，否则用 primary 色
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "确认",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
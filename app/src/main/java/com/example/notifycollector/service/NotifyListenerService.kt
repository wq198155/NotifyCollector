package com.example.notifycollector.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.notifycollector.NotifyApplication
import com.example.notifycollector.data.NotificationEntity
import com.example.notifycollector.data.SettingsStore
import com.example.notifycollector.util.RuleMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知监听服务：只读收集，绝不拦截/取消任何通知。
 * 命中用户分组规则的通知会被写入本地数据库，供 App 内分组展示。
 */
class NotifyListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** 自动回收限频：至少间隔 1 小时跑一次，避免每条通知都触发全表 UPDATE */
        @Volatile
        private var lastSweep = 0L
        private const val SWEEP_INTERVAL = 60 * 60 * 1000L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return // 忽略自身通知

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val repo = (application as NotifyApplication).repository
        scope.launch {
            // 顺手跑一次自动回收：把超过「历史保留天数」的通知搬进回收站（每小时至多一次）
            val now = System.currentTimeMillis()
            if (now - lastSweep > SWEEP_INTERVAL) {
                lastSweep = now
                val days = SettingsStore(application).recycleDays
                repo.recycleOld(now, days)
            }
            val groups = repo.allGroups()
            for (g in groups) {
                if (!RuleMatcher.matches(g, title, text)) continue
                val code = RuleMatcher.extractCode(g, title, text)
                // 带 codePattern 的分组（取件码 / 验证码等）必须真正提取到「数字取件号」才收集，
                // 避免「取件提醒」之类命中关键词却没有取件码的纯提醒短信被误收
                if (g.codePattern.isNotBlank() && code == null) continue
                repo.insertNotification(
                    NotificationEntity(
                        groupId = g.id,
                        packageName = sbn.packageName,
                        appName = safeAppName(sbn.packageName),
                        title = title,
                        text = text,
                        postTime = sbn.postTime,
                        extractedCode = code
                    )
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 仅收集，不需要处理移除事件
    }

    private fun safeAppName(pkg: String): String {
        return runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}

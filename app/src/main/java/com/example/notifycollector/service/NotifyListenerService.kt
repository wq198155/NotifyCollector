package com.example.notifycollector.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
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

    /**
     * 监听被系统断开（锁屏/内存压力杀进程常见）时主动请求重连，
     * 缩短「死亡窗口」——断连期间到达的通知无法补收，重连越快丢得越少。
     */
    override fun onListenerDisconnected() {
        runCatching {
            requestRebind(ComponentName(this, NotifyListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return // 忽略自身通知

        val (title, text) = extractContent(sbn)
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

    /**
     * 尽力提取标题与正文：优先 EXTRA_TITLE / EXTRA_TEXT，
     * 缺失时依次回退 EXTRA_BIG_TEXT、EXTRA_SUMMARY_TEXT 与 MessagingStyle 末条消息。
     *
     * 很多快递/驿站类 App（如【驿小哥】）把完整内容放在「大文本」或「会话样式」里，
     * EXTRA_TEXT 为空 → content="【驿小哥】 " 不含「中通/快递/取件」→ 直接不匹配、不收录。
     * 回退读取可避免这类通知被误判为「无内容」而丢弃。
     */
    private fun extractContent(sbn: StatusBarNotification): Pair<String, String> {
        val extras = sbn.notification.extras ?: return "" to ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        if (text.isBlank()) {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }?.let { text = it }
        }
        if (text.isBlank()) {
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }?.let { text = it }
        }
        if (text.isBlank()) {
            // 会话样式：取最后一条消息文本
            runCatching {
                NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(sbn.notification)
                    ?.messages?.lastOrNull()?.text?.toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { text = it }
        }
        return title to text.trim()
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

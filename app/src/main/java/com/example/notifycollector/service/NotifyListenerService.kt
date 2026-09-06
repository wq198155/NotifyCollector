package com.example.notifycollector.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.notifycollector.NotifyApplication
import com.example.notifycollector.data.GroupEntity
import com.example.notifycollector.data.NotificationEntity
import com.example.notifycollector.data.SettingsStore
import com.example.notifycollector.util.AiResult
import com.example.notifycollector.util.NotificationAiAnalyzer
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

        scope.launch {
            // 顺手跑一次自动回收：把超过「历史保留天数」的通知搬进回收站（每小时至多一次）
            val now = System.currentTimeMillis()
            if (now - lastSweep > SWEEP_INTERVAL) {
                lastSweep = now
                val days = SettingsStore(application).recycleDays
                repo().recycleOld(now, days)
            }
            process(sbn, title, text)
        }
    }

    private fun repo() = (application as NotifyApplication).repository

    /**
     * 正则优先 + 离线 AI 兜底：
     * - 先用现有分组正则匹配；匹配到的分组按原逻辑入库。
     * - 当「正则未命中任一分组」或「带 codePattern 的分组没提取到取件码」或用户设为「全量 AI」时，
     *   且已开启 AI，则调用本地模型做结构化抽取，用 AI 的取件码/公司/地址补全空字段；
     *   正则未命中但 AI 给出分类时，尝试归入同名/包含该分类的现有分组（提升召回）。
     * - AI 不可用 / 置信度过低 / 解析失败时自动退回正则结果，主流程不受影响、不崩溃。
     */
    private suspend fun process(sbn: StatusBarNotification, title: String, text: String) {
        val repo = repo()
        val settings = SettingsStore(application)
        val groups = repo.allGroups()

        val matched = groups.filter { RuleMatcher.matches(it, title, text) }
        val lowConf = matched.isEmpty() ||
            matched.any { it.codePattern.isNotBlank() && RuleMatcher.extractCode(it, title, text) == null }

        val needAi = settings.aiEnabled && (lowConf || !settings.aiLowConfOnly)
        val ai = if (needAi) {
            runCatching { NotificationAiAnalyzer.analyze(applicationContext, title, text) }.getOrNull()
        } else null

        if (matched.isNotEmpty()) {
            for (g in matched) {
                val regexCode = RuleMatcher.extractCode(g, title, text)
                val needsCode = g.codePattern.isNotBlank()
                // 正则命中、但本分组需要提取 code 却没提取到（如验证码隔了标点、字母验证码、
                // 或"不含验证码"之类仅字面提及）→ 交给本地 AI 进一步甄别：
                //   1) 让 AI 补提取 code（支持数字/字母、隔标点等正则难处理的情况）；
                //   2) 再让 AI 判断是否真的属于本分组，避免字面提及被误收。
                if (needsCode && regexCode == null) {
                    if (ai != null) {
                        val aiCode = ai.code
                        val aiBelongs = ai.category != null &&
                            (ai.category == g.name || ai.category in g.name)
                        // AI 能补提取到 code（数字/字母皆可），或 AI 明确判定属于本分组 → 入库；
                        // 否则（AI 既没提取到码、也不认为属于本组）→ 不入库，避免误收
                        // （如"不含验证码"之类仅字面提及、且无任何码可提取的通知）。
                        if (!aiCode.isNullOrBlank() || aiBelongs) {
                            val finalCode = aiCode ?: regexCode
                            repo.insertNotification(buildEntity(g, sbn, title, text, finalCode, ai, ai.category))
                        }
                    }
                    // AI 未开启则无 AI 可甄别，保持原行为：code 失败则丢弃
                    continue
                }
                // code 提取成功（或本分组不需要 code）→ 正常入库；AI 仍可补 code 用于展示
                var code = regexCode
                if (ai != null && code.isNullOrBlank()) code = ai.code
                repo.insertNotification(buildEntity(g, sbn, title, text, code, ai))
            }
        } else if (ai != null) {
            // 正则未命中，但 AI 给出分类 -> 归入同名/包含该分类的现有分组，提升召回
            val cat = ai.category
            if (!cat.isNullOrBlank()) {
                val target = groups.firstOrNull { it.name == cat }
                    ?: groups.firstOrNull { cat in it.name || it.name.contains(cat) }
                if (target != null) {
                    val code = ai.code
                    if (target.codePattern.isNotBlank() && code == null) return
                    repo.insertNotification(buildEntity(target, sbn, title, text, code, ai, cat))
                }
            }
        }
    }

    private fun buildEntity(
        g: GroupEntity,
        sbn: StatusBarNotification,
        title: String,
        text: String,
        code: String?,
        ai: AiResult?,
        aiCategory: String? = null
    ): NotificationEntity = NotificationEntity(
        groupId = g.id,
        packageName = sbn.packageName,
        appName = safeAppName(sbn.packageName),
        title = title,
        text = text,
        postTime = sbn.postTime,
        extractedCode = code,
        aiCompany = ai?.company,
        aiLocation = ai?.location,
        aiConfidence = ai?.confidence,
        aiUsed = ai != null,
        aiCategory = aiCategory
    )

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

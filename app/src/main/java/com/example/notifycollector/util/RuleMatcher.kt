package com.example.notifycollector.util

import com.example.notifycollector.data.GroupEntity
import com.example.notifycollector.data.MatchType

object RuleMatcher {

    /** 判断一条通知是否命中某分组规则 */
    fun matches(group: GroupEntity, title: String, text: String): Boolean {
        val content = "$title $text"
        return if (group.matchType == MatchType.KEYWORD) {
            content.contains(group.pattern, ignoreCase = true)
        } else {
            runCatching { Regex(group.pattern, RegexOption.IGNORE_CASE).containsMatchIn(content) }
                .getOrDefault(false)
        }
    }

    /**
     * 从通知中按 codePattern 提取验证码/取件码。
     * - 优先取第一个非空捕获组（兼容「锚定关键词」与「兜底裸数字」两种写法：
     *   取件码模板把"取件码xxx123"放第 1 组、"兜底裸数字"放第 2 组）。
     * - 长度护栏：剔除手机号(11位)/运单号(13+位)等长串数字，避免把电话或物流单号误当取件码。
     */
    fun extractCode(group: GroupEntity, title: String, text: String): String? {
        val pattern = group.codePattern.takeIf { it.isNotBlank() } ?: return null
        val content = "$title\n$text"
        return runCatching {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(content) ?: return@runCatching null
            // 取第一个非空捕获组；都没有才退而取整个匹配
            val captured = (1 until match.groups.size)
                .firstNotNullOfOrNull { match.groups[it]?.value?.takeIf { v -> v.isNotBlank() } }
                ?: match.value
            val digits = captured.replace(Regex("\\D"), "")
            if (digits.length !in 2..12 || digits.length == 11) null else captured
        }.getOrNull()
    }
}

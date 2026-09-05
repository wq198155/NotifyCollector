package com.example.notifycollector.util

import com.example.notifycollector.data.NotificationEntity

/**
 * 取件码卡片展示所需的解析结果。
 * - [code] 取件码（优先用接收时已提取的 [NotificationEntity.extractedCode]）
 * - [company] 快递公司名（中通/圆通/韵达/极兔…），实时从正文匹配
 * - [location] 快递站地点（如「春江新城鸿觉坊8栋101室」），实时从正文启发式提取
 */
data class ParcelInfo(
    val code: String?,
    val company: String?,
    val location: String?
)

/** 取件短信里常见的快递公司关键词，按出现顺序取第一个命中 */
private val COMPANY_KEYWORDS = listOf(
    "顺丰", "中通", "圆通", "韵达", "极兔", "申通", "邮政", "EMS",
    "京东", "德邦", "百世", "天天", "汇通", "优速", "快捷", "全峰", "宅急送", "中铁", "安能"
)

/** 是否含至少一个中文字符（CJK），用于剔除纯数字/英文的误抓（如手机号） */
private fun hasCJK(s: String): Boolean = s.any { it in '\u4e00'..'\u9fff' }

/**
 * 从一条通知里解析出取件码卡片所需的三要素。
 * 公司名与地点均为「尽力而为」的实时解析，解析不出时返回 null，由 UI 决定兜底展示。
 */
fun parseParcel(n: NotificationEntity): ParcelInfo {
    val code = n.extractedCode
    // 标题与正文都纳入匹配：部分通知公司名只在标题里（如「【中通快递】您的取件码…」）
    val haystack = "${n.title}\n${n.text}"
    val company = COMPANY_KEYWORDS.firstOrNull { haystack.contains(it) }
    val location = extractLocation(haystack)
    return ParcelInfo(code, company, location)
}

/**
 * 启发式提取快递站地点，依次尝试多套模式，返回第一个含中文的有效片段：
 * ① 「到/至/在/放…」与「取件码/室/栋/号/楼/驿站…」之间的文字
 * ② 含「室/栋/号/楼/单元/层」等地址后缀的短片段
 * ③ 含「驿站/丰巢/菜鸟」等驿站名的片段
 */
private fun extractLocation(text: String): String? {
    val patterns = listOf(
        // ① 到/至/位于/放/在/送/处 … 取件码/室/栋/号/楼/驿站/自提/丰巢/柜/菜鸟/，/换行
        Regex("(?:到|至|位于|放|在|送|处)\\s*([^，。,.\n]{2,40}?)\\s*(?:取件码|凭码|取件|包裹|室|栋|号|楼|单元|层|驿站|自提|丰巢|柜|菜鸟|，|\\n|$)"),
        // ② 含地址后缀的短片段
        Regex("([\\u4e00-\\u9fa5\\d]{2,30}?(?:室|栋|号|楼|单元|层))"),
        // ③ 驿站/自提点/丰巢/菜鸟 等
        Regex("([^，。,.\n]{2,30}?(?:驿站|自提点|自提柜|丰巢|菜鸟|超市|便利店|门面|门市))")
    )
    for (p in patterns) {
        val m = p.find(text) ?: continue
        val g = m.groupValues.getOrNull(1)?.trim().orEmpty()
        if (g.isNotBlank() && hasCJK(g)) return g
    }
    return null
}

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

/** 取件短信里常见的快递公司关键词，取正文中出现位置最靠前的一个 */
private val COMPANY_KEYWORDS = listOf(
    "顺丰", "中通", "圆通", "韵达", "极兔", "申通", "邮政", "EMS",
    "京东", "德邦", "百世", "天天", "汇通", "优速", "全峰", "宅急送", "中铁", "安能"
)

/** 是否含至少一个中文字符（CJK），用于剔除纯数字/英文的误抓（如手机号） */
private fun hasCJK(s: String): Boolean = s.any { it in '\u4e00'..'\u9fff' }

/**
 * 地址起点标记（「已到/送至/放在/位于…」）。
 * 注意：Java/Kotlin 正则的交替分支按书写顺序取第一个命中，所以**长的必须写在前面**，
 * 否则「已到达」会被单独的「到」抢先匹配，切出多余的「达」字。
 */
private val LOC_START = Regex(
    "已送达至|送达至|已到达|到达|已到|已送至|送至|送到|" +
        "已放到|放到|已放至|放至|已放置|放置在|放在|已放|放置|寄放|" +
        "位于|地址|位置|到|至|处"
)

/** 句子结束标记：句读标点 / 换行 / 明显的转折词。地址一律取到此处为止 */
private val SENT_END = Regex("[，,。;；、！!？?\\n]|凭|取件码|验证码")

/** 句读标点（用于定位地址片段的左边界） */
private val SENT_CHARS = charArrayOf('，', ',', '。', ';', '；', '、', '！', '!', '？', '?', '\n')

/** 地址特征后缀（用于没有「到/至」起点标记时反向定位地址）。
 *  「号」要求前面是数字，避免命中银行卡短信的「尾号1234」 */
private val ADDR_ANCHOR =
    Regex("\\d+号|室|栋|楼|单元|层|驿站|自提点|自提柜|丰巢|菜鸟|超市|便利店|门面|门市|店")

/**
 * 强地址特征：提取结果必须命中其一，否则视为误抓并返回 null。
 * 用于剔除「【招商银行】您尾号1234…消费人民币100.00元」这类金融短信。
 */
private val STRONG_ADDR =
    Regex("\\d+号|室|栋|楼|单元|层|驿站|自提|丰巢|菜鸟|超市|便利店|门面|门市|店")

/** 地址片段尾部常见的多余动词/提示语，提取后剔除 */
private val TAIL_NOISE = Regex("(?:取件码|取件|领取|领件|领取件|签收|自提|领取|领|取|拿)+$")

/** 地址片段开头常见的多余连接词/量词/残字 */
private val HEAD_NOISE = charArrayOf('的', '了', '达', '至', '在', '到', '：', ':', ' ')

/**
 * 清洗地址片段：去括号空白、去尾部动词、去开头连接词；
 * 清洗后长度不足 2 或不含中文则视为无效（返回 null）。
 */
private fun cleanLocation(raw: String): String? {
    var s = raw.trim()
    s = s.replace(TAIL_NOISE, "").trim()
    // 只在括号不配对时剥掉多余的一半；配对的括号是地址本身的一部分，需完整保留
    // 例：「丰巢柜（春江新城店）」保留全角括号，「丰巢柜（春江新城店」补掉多余的左括号
    if (s.startsWith("（") && !s.endsWith("）")) s = s.removePrefix("（")
    if (s.endsWith("）") && !s.contains("（")) s = s.removeSuffix("）")
    if (s.startsWith("(") && !s.endsWith(")")) s = s.removePrefix("(")
    if (s.endsWith(")") && !s.contains("(")) s = s.removeSuffix(")")
    s = s.trim().trimStart(*HEAD_NOISE).trim()
    if (s.length < 2 || !hasCJK(s)) return null
    // 必须含强地址特征，否则判为误抓（如金融类短信）
    if (!STRONG_ADDR.containsMatchIn(s)) return null
    return s
}

/**
 * 从 [from] 开始，一直取到句子结束（[SENT_END]）或串尾，并清洗后返回。
 */
private fun takeUntilSentenceEnd(text: String, from: Int): String? {
    if (from >= text.length) return null
    val rest = text.substring(from)
    val endM = SENT_END.find(rest)
    val raw = if (endM == null) rest else rest.substring(0, endM.range.first)
    return cleanLocation(raw)
}

/**
 * 启发式提取快递站地点，**取到句子结束**：
 * ① 先找「已到 / 送达至 / 放在 / 位于…」等起点标记，从标记后一直取到句子结束标点
 *    （如「已到春江新城一期鸿觉坊8栋101室顺丰，凭…」→「春江新城一期鸿觉坊8栋101室顺丰」）
 * ② 没有起点标记时，反向定位：先找「室/栋/驿站/丰巢…」等地标词，
 *    再往前推到最近的一个句子起点，往后取到句子结束
 */
private fun extractLocation(text: String): String? {
    // ① 起点标记优先
    val start = LOC_START.find(text)
    if (start != null) {
        takeUntilSentenceEnd(text, start.range.last + 1)?.let { return it }
    }
    // ② 反向定位：由地标词往前推到句子开头，再取到句子结束
    val anchor = ADDR_ANCHOR.find(text) ?: return null
    val head = text.substring(0, anchor.range.first)
    val begin = maxOf(
        head.lastIndexOfAny(SENT_CHARS) + 1,
        LOC_START.findAll(head).lastOrNull()?.range?.last?.plus(1) ?: 0
    )
    return takeUntilSentenceEnd(text, begin)
}

/**
 * 从一条通知里解析出取件码卡片所需的三要素。
 * 公司名与地点均为「尽力而为」的实时解析，解析不出时返回 null，由 UI 决定兜底展示。
 */
fun parseParcel(n: NotificationEntity): ParcelInfo {
    val code = n.extractedCode
    // 标题与正文都纳入匹配：部分通知公司名只在标题里（如「【中通快递】您的取件码…」）
    val haystack = "${n.title}\n${n.text}"
    // 取正文中「出现位置最靠前」的公司名，而非按关键词列表顺序：
    // 例「您的快递中通，已到…室顺丰，凭…」中通在前 → 中通；顺丰只是店名的一部分
    val company = COMPANY_KEYWORDS
        .map { kw -> kw to haystack.indexOf(kw) }
        .filter { (_, idx) -> idx >= 0 }
        .minByOrNull { (_, idx) -> idx }
        ?.first
    val location = extractLocation(haystack)
    return ParcelInfo(code, company, location)
}


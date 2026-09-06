package com.example.notifycollector.data

/**
 * 离线 AI 解析相关配置。
 * 模型为本地运行的开源 Gemma（int4 量化），首次运行在 Wi-Fi 下下载到应用私有目录，
 * 推理全程不联网，通知内容不会离开本机。
 */
object AiConfig {
    /** 下载后落盘的文件名（位于 filesDir/ai/ 下） */
    const val MODEL_FILENAME = "model.task"

    /** 单次推理最大生成 token 数；通知解析输出很短，512 足够 */
    const val MAX_TOKENS = 512

    /** AI 输出置信度低于该值视为不可信，退回正则结果 */
    const val MIN_CONFIDENCE = 0.5

    /**
     * 官方推荐模型地址（默认下载直链）。
     * 这是一台自托管服务器上的 Gemma-3 1B int4 量化模型，可在「设置 → AI 智能解析」中一键下载。
     * 用户也可在「模型地址」文本框里填写任意第三方兼容的 .task 模型地址按需下载。
     */
    const val DEFAULT_MODEL_URL = "https://m.152727.xyz:8443/ai/gemma3-1b-it-int4.task"

    /**
     * 设置页「支持哪些模型」说明（纯文本逐行展示）。
     * 本应用基于 MediaPipe LLM Inference 在手机本地离线推理，仅支持 Gemma 系列 .task 模型。
     */
    val SUPPORTED_MODELS: List<String> = listOf(
        "本应用基于 MediaPipe LLM Inference 在手机本地离线推理，全程不联网。",
        "仅支持已转换为 .task 格式的 Gemma 系列模型：",
        "• Gemma-3 1B / 2B（int4 量化，推荐：体积小、响应快）",
        "• Gemma-2 2B / 9B（int4 量化）",
        "模型须为 int4 量化、CPU 可用的 .task 文件。",
        "不支持：PyTorch(.bin/.safetensors)、GGUF，以及 GPT / 通义 / 文心等云端模型。"
    )

    /**
     * 校验用户填写的模型地址是否「可能可用」（格式/家族层面的前置检查）。
     * 返回 null 表示通过；否则返回给用户的中文提示。
     * 注意：最终能否真正加载，由「验证模型」按钮（NotificationAiAnalyzer.verifyModel）决定。
     */
    fun validateModelUrl(raw: String): String? {
        val u = raw.trim()
        if (u.isBlank()) return null // 留空=使用官方推荐模型，不算错误
        if (!u.endsWith(".task", ignoreCase = true))
            return "模型须为 MediaPipe .task 文件（当前仅支持 Gemma 系列 .task），请检查地址后缀"
        if (!u.contains("gemma", ignoreCase = true))
            return "建议选择 Gemma 系列 .task 模型，其他架构可能无法被当前框架加载"
        return null
    }

    /** 构造给模型的提示词：要求只输出一个 JSON 对象 */
    fun buildPrompt(title: String, text: String): String = buildString {
        appendLine("你是中文手机通知解析器。给定一条通知的标题与正文，提取结构化字段，只输出一个 JSON 对象，不要任何解释、不要 markdown 代码块。")
        appendLine("字段说明：")
        appendLine("- category: 最匹配的分类，取值之一：取件码 / 验证码 / 银行动账 / 其他")
        appendLine("- code: 取件码或验证码的数字串（如 11-2-4682、8842），没有则为空字符串")
        appendLine("- company: 快递或承运公司名（如 中通、顺丰、韵达、EMS），没有则为空字符串")
        appendLine("- location: 取件地址，取到房间号/楼栋/驿站为止（如 春江新城一期鸿觉坊8栋101室顺丰），没有则为空字符串")
        appendLine("- confidence: 你对以上判断的把握，0 到 1 之间的小数")
        appendLine()
        appendLine("通知标题：$title")
        appendLine("通知正文：$text")
        appendLine()
        appendLine("JSON:")
    }
}

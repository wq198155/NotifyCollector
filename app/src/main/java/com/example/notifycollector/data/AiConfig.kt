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
     * 默认模型地址（留空）。
     * MediaPipe 官方 GCS 路径已整体下架、HuggingFace 需登录授权，公共免鉴权直链不稳定，
     * 因此默认不填。推荐二选一：
     *  1) 自托管：把 model.task 放到你自己的服务器（如 https://你的域名/models/gemma-2b-it-cpu-int4.task），
     *     在「设置 → AI 智能解析 → 模型地址」填写该地址后点「下载模型」；
     *  2) 或点「从本机导入」，把电脑上下好的 .task 通过微信/数据线传到手机后直接导入（完全不联网）。
     */
    const val DEFAULT_MODEL_URL = ""

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

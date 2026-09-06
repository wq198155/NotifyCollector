package com.example.notifycollector.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 轻量设置存储（SharedPreferences 实现，无额外依赖）。
 * 目前仅持久化「历史保留天数」——超过该天数的通知自动进入回收站。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 历史保留天数，默认 [DEFAULT_RECYCLE_DAYS] 天 */
    var recycleDays: Int
        get() = prefs.getInt(KEY_RECYCLE_DAYS, DEFAULT_RECYCLE_DAYS)
        set(value) {
            val clamped = value.coerceIn(MIN_RECYCLE_DAYS, MAX_RECYCLE_DAYS)
            prefs.edit().putInt(KEY_RECYCLE_DAYS, clamped).apply()
        }

    /** 是否启用离线 AI 智能解析（默认关，需先下载模型） */
    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, DEFAULT_AI_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    /**
     * 仅低置信时调用 AI：开启后只有「正则未命中 / 取件码没提出来」的通知才走 AI，
     * 绝大多数已知模板仍由正则瞬时处理，省电省时。关闭则每条通知都过 AI（最准但最耗）。
     */
    var aiLowConfOnly: Boolean
        get() = prefs.getBoolean(KEY_AI_LOW_CONF, DEFAULT_AI_LOW_CONF)
        set(value) = prefs.edit().putBoolean(KEY_AI_LOW_CONF, value).apply()

    /** 模型下载地址（默认 MediaPipe 官方 Gemma-2B，可改成可用镜像） */
    var aiModelUrl: String
        get() = prefs.getString(KEY_AI_MODEL_URL, AiConfig.DEFAULT_MODEL_URL)
            ?: AiConfig.DEFAULT_MODEL_URL
        set(value) = prefs.edit().putString(KEY_AI_MODEL_URL, value).apply()

    companion object {
        private const val PREFS_NAME = "nc_settings"
        private const val KEY_RECYCLE_DAYS = "recycle_days"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_LOW_CONF = "ai_low_conf"
        private const val KEY_AI_MODEL_URL = "ai_model_url"

        const val DEFAULT_RECYCLE_DAYS = 14
        const val MIN_RECYCLE_DAYS = 1
        const val MAX_RECYCLE_DAYS = 365

        const val DEFAULT_AI_ENABLED = false
        const val DEFAULT_AI_LOW_CONF = true
    }
}

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

    companion object {
        private const val PREFS_NAME = "nc_settings"
        private const val KEY_RECYCLE_DAYS = "recycle_days"

        const val DEFAULT_RECYCLE_DAYS = 14
        const val MIN_RECYCLE_DAYS = 1
        const val MAX_RECYCLE_DAYS = 365
    }
}

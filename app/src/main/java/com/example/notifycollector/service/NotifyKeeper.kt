package com.example.notifycollector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.notifycollector.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * 后台保活辅助：
 * - 创建通知通道（常驻保活通知 / 重要提醒通知）。
 * - 构建常驻前台通知（让监听服务升至前台优先级，三星等系统最不敢杀）。
 * - 检测通知监听权限是否被收回。
 * - 权限丢失时弹高优通知引导用户重新开启（限频，避免刷屏）。
 * - 注册 15 分钟周期的看门狗 Worker，自动重绑 / 感知丢失。
 */
object NotifyKeeper {

    const val CHANNEL_KEEPALIVE = "nc_keepalive"
    const val CHANNEL_ALERT = "nc_alert"
    const val NOTIF_KEEPALIVE_ID = 1          // 常驻前台通知（不可划掉）
    const val NOTIF_ALERT_ID = 2              // 权限丢失提醒（高优，可划掉）
    private const val PROMPT_COOLDOWN = 24L * 60 * 60 * 1000 // 丢失提醒限频：每天至多一次
    private const val PREFS = "nc_keeper"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_KEEPALIVE) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_KEEPALIVE,
                        "后台保活",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "保持通知监听在后台运行" }
                )
            }
            if (mgr.getNotificationChannel(CHANNEL_ALERT) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERT,
                        "重要提醒",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
    }

    /** 常驻前台通知：让用户明确知道服务在跑，且提升进程优先级防止被杀 */
    fun buildKeepAliveNotification(ctx: Context): Notification {
        val intent = Intent(ctx, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CHANNEL_KEEPALIVE)
            .setContentTitle("通知接收器正在后台运行")
            .setContentText("保持通知监听，请勿清理此通知")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 通知监听权限（设置 > 通知使用权）是否仍开启 */
    fun isListenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val me = ComponentName(ctx, NotifyListenerService::class.java).flattenToString()
        return flat.split(":").any { it.equals(me, ignoreCase = true) }
    }

    /** 权限丢失时弹高优通知，点击直达「通知使用权」设置页 */
    fun notifyPermissionLost(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_PROMPT, 0) < PROMPT_COOLDOWN) return
        prefs.edit().putLong(KEY_LAST_PROMPT, now).apply()

        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ALERT)
            .setContentTitle("通知监听已关闭")
            .setContentText("点击重新开启，否则本应用无法接收通知")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ALERT_ID, notif)
    }

    /** 注册看门狗：每 15 分钟检查一次监听状态，断则重绑、丢则提醒 */
    fun scheduleWork(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "nc_keepalive",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    private val KEY_LAST_PROMPT = "last_lost_prompt"
}

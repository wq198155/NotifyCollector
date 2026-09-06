package com.example.notifycollector.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * 独立的前台保活服务：只负责挂一条常驻通知，把进程抬到前台优先级，
 * 让三星/OEM 最不敢杀。与 NotifyListenerService 解耦——监听服务保持纯净，
 * 避免在其上声明 foregroundServiceType 导致系统拒绝启动监听（已验证会失效）。
 *
 * 仅在【App 进入前台（MainActivity）】时启动：前台上下文启动 FGS 不受 Android 12+
 * 后台启动限制；启动后靠 START_STICKY 在 App 关闭后依然常驻。
 * 后台场景（开机/看门狗）不在此启动，避免触发 ForegroundServiceDidNotStartInTime 崩溃。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotifyKeeper.createChannels(this)
        try {
            val notif = NotifyKeeper.buildKeepAliveNotification(this)
            if (Build.VERSION.SDK_INT >= 34) {
                // 0x40000000 = FOREGROUND_SERVICE_TYPE_SPECIAL_USE（须与 Manifest 中声明一致；
                // 并在 Manifest 声明 FOREGROUND_SERVICE_SPECIAL_USE 权限，否则 Android 14+ 抛 SecurityException）
                startForeground(NotifyKeeper.NOTIF_KEEPALIVE_ID, notif, 0x40000000)
            } else {
                startForeground(NotifyKeeper.NOTIF_KEEPALIVE_ID, notif)
            }
        } catch (e: Exception) {
            // 前台启动失败（极少数受限场景）不影响监听，仅失去常驻保活一层
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被杀后系统按 START_STICKY 重建，缩短死亡窗口
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    companion object {
        /** 启动常驻保活前台服务（幂等，已运行则忽略；仅在前台上下文调用） */
        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, KeepAliveService::class.java)) }
        }

        /** 停止常驻保活服务 */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) }
        }
    }
}

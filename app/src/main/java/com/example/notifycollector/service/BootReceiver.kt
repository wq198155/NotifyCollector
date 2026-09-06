package com.example.notifycollector.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后系统不会自动重绑通知监听，需要主动 requestRebind；
 * 同时重新登记看门狗调度（WorkManager 自身会在重启后保留任务，
 * 这里再保险地补一次，确保万无一失）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            NotifyListenerService.requestRebindStatic(context)
            NotifyKeeper.scheduleWork(context)
            // 注：常驻保活前台服务仅在 App 进入前台时启动（避免后台启动受限崩溃），
            // 这里不启动 KeepAliveService；看门狗/重绑已足够在开机后恢复监听。
        }
    }
}

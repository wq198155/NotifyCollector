package com.example.notifycollector.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 看门狗：每 15 分钟跑一次（WorkManager 周期任务最小间隔）。
 * - 若通知监听权限被收回 -> 弹高优通知引导用户重新开启。
 * - 若权限仍在但未连接 -> 请求系统重绑（幂等，已连接时不重复触发）。
 * 这样即使进程被系统/OEM 杀掉后没自动重启，也能在 15 分钟内自愈。
 */
class KeepAliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NotifyKeeper.isListenerEnabled(applicationContext)) {
            // 权限被收回：提醒用户，不再尝试重绑（没授权重绑无效）
            NotifyKeeper.notifyPermissionLost(applicationContext)
            return Result.success()
        }
        // 权限在，但服务可能未连接（进程被杀/断连未触发回调）-> 主动重绑
        if (!NotifyListenerService.isConnected) {
            NotifyListenerService.requestRebindStatic(applicationContext)
        }
        return Result.success()
    }
}

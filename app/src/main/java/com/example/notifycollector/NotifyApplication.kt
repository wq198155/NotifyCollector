package com.example.notifycollector

import android.app.Application
import com.example.notifycollector.data.AppDatabase
import com.example.notifycollector.data.Repository
import com.example.notifycollector.service.NotifyKeeper

class NotifyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { Repository(database.groupDao(), database.notificationDao()) }

    override fun onCreate() {
        super.onCreate()
        // 登记 15 分钟看门狗，保障监听被杀/断连后能自愈、权限丢失能提醒
        NotifyKeeper.scheduleWork(this)
    }
}

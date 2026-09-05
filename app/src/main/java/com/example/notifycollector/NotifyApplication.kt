package com.example.notifycollector

import android.app.Application
import com.example.notifycollector.data.AppDatabase
import com.example.notifycollector.data.Repository

class NotifyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { Repository(database.groupDao(), database.notificationDao()) }
}

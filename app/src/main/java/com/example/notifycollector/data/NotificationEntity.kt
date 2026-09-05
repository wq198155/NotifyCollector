package com.example.notifycollector.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    /** 命中后按 codePattern 提取出的验证码/取件码，无则 null */
    val extractedCode: String? = null,
    /**
     * 回收站标记：进入回收站时写入当时时间戳（毫秒），正常在列时为 null。
     * 超过 14 天的通知会被自动搬进回收站（recycledAt 置为当时时间），
     * 之后在分组详情中不再展示，仅出现在回收站里。
     */
    val recycledAt: Long? = null,
    /**
     * 是否已读：左滑菜单"已读"后置为 true，列表项变灰并排序到该分组末尾。
     * 默认 false（未读）。Room 将 Boolean 映射为 INTEGER，0=false/1=true。
     */
    @ColumnInfo(defaultValue = "0")
    val read: Boolean = false
)

package com.example.notifycollector.data

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * 分组 + 该分组下已收集通知数量。
 * 由 GroupDao.groupsWithCount() 的联表查询映射。
 */
data class GroupWithCount(
    @Embedded val group: GroupEntity,
    /** 该分组下全部未回收通知数量（删除确认等场景使用） */
    @ColumnInfo(name = "notifCount") val notifCount: Int,
    /** 未读通知数量（首页角标使用，已读不计） */
    @ColumnInfo(name = "unreadCount") val unreadCount: Int
)

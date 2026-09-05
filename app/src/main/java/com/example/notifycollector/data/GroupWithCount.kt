package com.example.notifycollector.data

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * 分组 + 该分组下已收集通知数量。
 * 由 GroupDao.groupsWithCount() 的联表查询映射。
 */
data class GroupWithCount(
    @Embedded val group: GroupEntity,
    @ColumnInfo(name = "notifCount") val notifCount: Int
)

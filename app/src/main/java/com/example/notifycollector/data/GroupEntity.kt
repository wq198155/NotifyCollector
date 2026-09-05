package com.example.notifycollector.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "KEYWORD" 或 "REGEX"，见 [MatchType] */
    val matchType: String,
    /** 关键字或正则表达式 */
    val pattern: String,
    /** 可选：用于提取验证码/取件码的正则，需含一个捕获组；为空则不提取卡片 */
    val codePattern: String = "",
    /**
     * 有效期（分钟）：命中通知在 postTime + expireMinutes 之后视为过期，列表/卡片置灰。
     * 0 表示永不过期（默认）。典型用法：验证码设 5 分钟。
     */
    @ColumnInfo(defaultValue = "0")
    val expireMinutes: Int = 0,
    /**
     * 分组在首页列表中的排序权重，越小越靠前。
     * 0 为默认值（从未手动排序），此时按 createdAt DESC 显示（新分组在顶部）。
     * 用户在首页手动排序并"保存"后，会按当前顺序改写为 0,1,2…。
     */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 判断某条通知（按其发生时间）是否已过期 */
    fun isExpired(postTime: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (expireMinutes <= 0) return false
        return now > postTime + expireMinutes * 60_000L
    }
}

object MatchType {
    const val KEYWORD = "KEYWORD"
    const val REGEX = "REGEX"
}

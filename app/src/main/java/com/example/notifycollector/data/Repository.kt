package com.example.notifycollector.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

class Repository(
    private val groupDao: GroupDao,
    private val notificationDao: NotificationDao
) {
    companion object {
        /** 超过该天数的通知自动搬进回收站 */
        const val RECYCLE_DAYS = 14
    }

    /**
     * 监听器在双配置文件（如安全文件夹 / 工作资料）设备上会被系统绑定多次，
     * 同一次通知会并发触发多回 onNotificationPosted。若"先查重再插入"不串行化，
     * 多个协程会在彼此提交前都查到"无重复"，从而写入多条相同记录（实测踩过）。
     */
    private val insertLock = Mutex()
    fun groupsWithCount() = groupDao.groupsWithCount()
    suspend fun allGroups() = groupDao.getAll()

    suspend fun groupById(id: Long) = groupDao.getById(id)
    fun groupFlow(id: Long) = groupDao.groupByIdFlow(id)

    suspend fun updateGroup(g: GroupEntity) = groupDao.update(g)

    /** 只清空该分组下已收集的通知，保留分组本身 */
    suspend fun clearGroup(groupId: Long) = notificationDao.deleteForGroup(groupId)

    /** 添加一个模板分组；同名已存在则返回 false（不重复创建） */
    suspend fun addPreset(p: PresetGroup): Boolean {
        if (groupDao.countByName(p.name) > 0) return false
        insertGroup(
            GroupEntity(
                name = p.name,
                matchType = p.matchType,
                pattern = p.pattern,
                codePattern = p.codePattern,
                expireMinutes = p.expireMinutes,
                cardView = p.cardView
            )
        )
        return true
    }

    /**
     * 写入一条分组；排序权重自动接在现有最大权重之后，保证新分组默认出现在列表底部，
     * 不会打乱用户已手动排好的顺序。
     */
    suspend fun insertGroup(g: GroupEntity): Long {
        val maxOrder = groupDao.getAll().maxOfOrNull { it.sortOrder } ?: -1
        return groupDao.insert(g.copy(sortOrder = maxOrder + 1))
    }

    /** 保存首页手动拖拽后的分组顺序：依传入 id 列表改写为 0,1,2… */
    suspend fun saveGroupOrder(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        val all = groupDao.getAll().associateBy { it.id }
        val updated = orderedIds.mapIndexedNotNull { idx, id -> all[id]?.copy(sortOrder = idx) }
        groupDao.updateGroups(updated)
    }

    suspend fun deleteGroup(g: GroupEntity) {
        groupDao.delete(g)
        notificationDao.deleteForGroup(g.id)
    }

    fun notificationsForGroup(groupId: Long) = notificationDao.forGroup(groupId)
    fun notificationById(id: Long) = notificationDao.byId(id)

    /** 左滑"已读"：切换单条通知的已读状态（未读→置灰沉底；已读→恢复） */
    suspend fun setRead(id: Long, read: Boolean) = notificationDao.setRead(id, read)

    /** 写入前做去重（加锁保证并发安全）：
     *  1) 60s 内同 包名+标题+正文 视为重复；
     *  2) 当天同分组内已存在相同取件码也视为重复（多个软件推送同一条取件通知时只留一条）。 */
    suspend fun insertNotification(n: NotificationEntity): Long = insertLock.withLock {
        val since = n.postTime - 60_000
        val dup = notificationDao.existsRecent(n.groupId, n.packageName, n.title, n.text, since)
        if (dup > 0) return@withLock -1L
        val code = n.extractedCode
        if (!code.isNullOrBlank()) {
            val dayStart = dayStartMillis(n.postTime)
            if (notificationDao.existsSameCodeToday(n.groupId, code, dayStart) > 0) return@withLock -1L
        }
        return@withLock notificationDao.insert(n)
    }

    /** 取本地时区当天 0 点对应的毫秒时间戳 */
    private fun dayStartMillis(ts: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    suspend fun deleteNotification(n: NotificationEntity) = notificationDao.delete(n)

    // —— 回收站 ——

    fun recycledAll() = notificationDao.recycledAll()
    fun recycledCount() = notificationDao.recycledCount()

    /**
     * 将超过 [days] 天的"在列"通知搬进回收站；返回被搬移的条数。
     * 通过回收站标记 recycledAt 实现软删除，原分组归属保留、可恢复。
     * [days] 来自设置（默认 [RECYCLE_DAYS]）。
     */
    suspend fun recycleOld(
        now: Long = System.currentTimeMillis(),
        days: Int = RECYCLE_DAYS
    ): Int {
        val cutoff = now - days * 24 * 60 * 60 * 1000L
        return notificationDao.recycleOld(cutoff, now)
    }

    /**
     * 用备份分组整组替换当前配置：先清空分组表与通知表（避免旧分组 id 残留孤儿通知），
     * 再写入备份里的分组（id 归零由 Room 重新自增，其余字段原样保留）。
     */
    suspend fun replaceAllGroups(groups: List<GroupEntity>) {
        groupDao.deleteAll()
        notificationDao.deleteAll()
        groups.forEach { groupDao.insert(it.copy(id = 0)) }
    }

    suspend fun restoreRecycled(id: Long) = notificationDao.restoreRecycled(id)
    suspend fun deleteRecycled(id: Long) = notificationDao.deleteRecycled(id)
    suspend fun clearRecycled() = notificationDao.clearRecycled()

    /** 清理超过 days 天的历史通知，避免长期堆积 */
    suspend fun purgeOlderThan(days: Int) {
        if (days <= 0) return
        val before = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        notificationDao.deleteBefore(before)
    }
}

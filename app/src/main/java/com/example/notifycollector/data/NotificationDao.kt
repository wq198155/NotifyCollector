package com.example.notifycollector.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE groupId = :groupId AND recycledAt IS NULL ORDER BY read ASC, postTime DESC")
    fun forGroup(groupId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    fun byId(id: Long): Flow<NotificationEntity?>

    @Insert
    suspend fun insert(n: NotificationEntity): Long

    /** 左滑"已读"：反转单条通知的已读状态 */
    @Query("UPDATE notifications SET read = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    /** 60 秒内同 包名+标题+正文 视为重复，避免同一条通知被反复写入 */
    @Query(
        "SELECT COUNT(*) FROM notifications " +
            "WHERE groupId = :groupId AND packageName = :pkg AND title = :title AND text = :text AND postTime > :since"
    )
    suspend fun existsRecent(groupId: Long, pkg: String, title: String, text: String, since: Long): Int

    /** 当天(本地 0 点起)同分组内已存在相同取件码则视为重复，多个软件推送同一条取件通知时只留一条 */
    @Query(
        "SELECT COUNT(*) FROM notifications " +
            "WHERE groupId = :groupId AND extractedCode = :code AND postTime >= :dayStart"
    )
    suspend fun existsSameCodeToday(groupId: Long, code: String, dayStart: Long): Int

    @Delete
    suspend fun delete(n: NotificationEntity)

    @Query("DELETE FROM notifications WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: Long)

    /** 清理早于某个时间点的历史通知 */
    @Query("DELETE FROM notifications WHERE postTime < :before")
    suspend fun deleteBefore(before: Long)

    /** 回收站：所有已回收（recycledAt 非空）的通知，按移入时间倒序 */
    @Query("SELECT * FROM notifications WHERE recycledAt IS NOT NULL ORDER BY recycledAt DESC")
    fun recycledAll(): Flow<List<NotificationEntity>>

    /** 回收站条目数（用于角标/空态判断） */
    @Query("SELECT COUNT(*) FROM notifications WHERE recycledAt IS NOT NULL")
    fun recycledCount(): Flow<Int>

    /** 将超过 before 的"在列"通知搬进回收站，写入移入时间 now；返回被搬移的条数 */
    @Query(
        "UPDATE notifications SET recycledAt = :now " +
            "WHERE recycledAt IS NULL AND postTime < :before"
    )
    suspend fun recycleOld(before: Long, now: Long): Int

    /** 从回收站恢复：清空 recycledAt，回到原分组 */
    @Query("UPDATE notifications SET recycledAt = NULL WHERE id = :id")
    suspend fun restoreRecycled(id: Long)

    /** 彻底删除回收站中的单条（仅当它确实在回收站里） */
    @Query("DELETE FROM notifications WHERE id = :id AND recycledAt IS NOT NULL")
    suspend fun deleteRecycled(id: Long)

    /** 清空整个回收站（所有已回收条目永久删除） */
    @Query("DELETE FROM notifications WHERE recycledAt IS NOT NULL")
    suspend fun clearRecycled()

    /** 清空整张通知表（导入备份前先全清，避免残留旧分组 id 对应的孤儿数据） */
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}

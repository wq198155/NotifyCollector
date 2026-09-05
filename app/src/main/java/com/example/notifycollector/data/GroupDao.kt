package com.example.notifycollector.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query(
        "SELECT groups.*, " +
            "(SELECT COUNT(*) FROM notifications WHERE notifications.groupId = groups.id " +
            "AND notifications.recycledAt IS NULL) AS notifCount " +
            "FROM groups ORDER BY groups.sortOrder ASC, groups.createdAt DESC"
    )
    fun groupsWithCount(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM groups")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    /** 分组详情/过期判断需要随数据变化实时刷新，故用 Flow */
    @Query("SELECT * FROM groups WHERE id = :id")
    fun groupByIdFlow(id: Long): Flow<GroupEntity?>

    /** 模板去重：同名分组已存在则不再插入 */
    @Query("SELECT COUNT(*) FROM groups WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    /** 批量更新（保存首页手动排序后的顺序） */
    @Update
    suspend fun updateGroups(groups: List<GroupEntity>)

    @Delete
    suspend fun delete(group: GroupEntity)

    /** 清空整个分组表（导入备份时先全清再写入） */
    @Query("DELETE FROM groups")
    suspend fun deleteAll()
}

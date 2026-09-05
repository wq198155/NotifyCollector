package com.example.notifycollector.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notifycollector.NotifyApplication
import com.example.notifycollector.data.GroupEntity
import com.example.notifycollector.data.GroupWithCount
import com.example.notifycollector.data.NotificationEntity
import com.example.notifycollector.data.PresetGroup
import com.example.notifycollector.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifyApplication).repository
    private val settings = SettingsStore(app)

    /** 历史保留天数（设置页可改，自动回收按此值执行） */
    var recycleDays by mutableStateOf(settings.recycleDays)
        private set

    fun updateRecycleDays(days: Int) {
        settings.recycleDays = days
        recycleDays = settings.recycleDays
    }

    init {
        // 应用启动即跑一次自动回收：把超过「历史保留天数」的通知搬进回收站
        viewModelScope.launch { repo.recycleOld(days = settings.recycleDays) }
    }

    /** 导出/导入备份：读取全部分组配置 */
    suspend fun getAllGroups(): List<GroupEntity> = repo.allGroups()

    /** 导入备份：用备份分组整组替换当前配置 */
    suspend fun replaceAllGroups(groups: List<GroupEntity>) = repo.replaceAllGroups(groups)

    fun groupsWithCount(): Flow<List<GroupWithCount>> = repo.groupsWithCount()
    fun notificationsForGroup(groupId: Long): Flow<List<NotificationEntity>> = repo.notificationsForGroup(groupId)
    fun notificationById(id: Long): Flow<NotificationEntity?> = repo.notificationById(id)
    fun groupFlow(id: Long): Flow<GroupEntity?> = repo.groupFlow(id)

    fun addGroup(
        name: String,
        matchType: String,
        pattern: String,
        codePattern: String,
        expireMinutes: Int = 0,
        cardView: Boolean = false
    ) {
        viewModelScope.launch {
            repo.insertGroup(
                GroupEntity(
                    name = name,
                    matchType = matchType,
                    pattern = pattern,
                    codePattern = codePattern,
                    expireMinutes = expireMinutes,
                    cardView = cardView
                )
            )
        }
    }

    /** 一键添加模板分组；onResult 返回 false 表示同名分组已存在 */
    fun addPreset(p: PresetGroup, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repo.addPreset(p))
        }
    }

    fun updateGroup(g: GroupEntity) {
        viewModelScope.launch { repo.updateGroup(g) }
    }

    /** 清空分组内的通知，保留分组本身 */
    fun clearGroup(groupId: Long) {
        viewModelScope.launch { repo.clearGroup(groupId) }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch { repo.deleteGroup(group) }
    }

    /** 保存首页手动拖拽后的分组顺序（传入分组 id 的当前顺序） */
    fun saveGroupOrder(orderedIds: List<Long>) {
        viewModelScope.launch { repo.saveGroupOrder(orderedIds) }
    }

    fun deleteNotification(n: NotificationEntity) {
        viewModelScope.launch { repo.deleteNotification(n) }
    }

    /** 左滑"已读"：切换单条已读状态 */
    fun markRead(n: NotificationEntity) {
        viewModelScope.launch { repo.setRead(n.id, !n.read) }
    }

    // —— 回收站 ——

    /** 回收站全部条目（按移入时间倒序） */
    fun recycledAll(): Flow<List<NotificationEntity>> = repo.recycledAll()

    /** 回收站条目数（用于角标/空态） */
    fun recycledCount(): Flow<Int> = repo.recycledCount()

    /** 手动触发一次自动回收（进入回收站页时调用，保证>历史保留天数已搬入） */
    fun sweepRecycleBin(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.recycleOld(days = settings.recycleDays)) }
    }

    /** 从回收站恢复单条：回到原分组 */
    fun restoreRecycled(id: Long) {
        viewModelScope.launch { repo.restoreRecycled(id) }
    }

    /** 彻底删除回收站中的单条 */
    fun deleteRecycled(n: NotificationEntity) {
        viewModelScope.launch { repo.deleteRecycled(n.id) }
    }

    /** 清空整个回收站 */
    fun clearRecycled() {
        viewModelScope.launch { repo.clearRecycled() }
    }
}

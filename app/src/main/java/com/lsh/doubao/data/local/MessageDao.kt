package com.lsh.doubao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lsh.doubao.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // 获取所有消息，按时间顺序排列
    // 返回 Flow，意味着这是一个"实时数据流"
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    // 获取最近的 N 条消息 (用于构建发给 AI 的上下文)
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 10): List<Message>

    // 插入消息
    // OnConflictStrategy.REPLACE: 如果 ID 重复，就覆盖旧的
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    // 更新消息 (用于流式接收时不断更新内容)
    @Update
    suspend fun updateMessage(message: Message)

    // 清空对话
    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
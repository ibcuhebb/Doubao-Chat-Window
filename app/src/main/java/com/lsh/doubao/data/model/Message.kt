package com.lsh.doubao.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 聊天消息实体类
 *
 * 对应 API 中的 message 对象。
 * 为了适配 Android UI 展示和 Room 数据库存储，将 API 中复杂的嵌套结构简化为扁平的字段
 */
@Entity(tableName = "messages")
data class Message(
    // 唯一标识符，用于 DiffUtil 对比和数据库主键
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // 消息角色 (user / assistant)
    val role: MessageRole,

    // 消息正文内容
    var content: String = "",

    // 深度思考/推理内容
    var reasoningContent: String? = null,

    // 图片 URL
    val imageUrl: String? = null,

    // 消息时间戳
    val timestamp: Long = System.currentTimeMillis(),

    // 消息状态
    var status: MessageStatus = MessageStatus.SENDING
)

/**
 * 角色枚举：定义消息是谁发的
 */
enum class MessageRole(val apiValue: String) {
    USER("user"),          // 用户
    ASSISTANT("assistant"), // AI
    SYSTEM("system");      // 系统预设

    companion object {
        // 辅助方法：从 API 字符串转回枚举
        fun fromApiValue(value: String): MessageRole {
            return entries.find { it.apiValue == value } ?: ASSISTANT
        }
    }
}

/**
 * 状态枚举：定义消息当前的发送状态
 */
enum class MessageStatus {
    SENDING, // 正在发送/正在生成 (显示转圈)
    SUCCESS, // 发送/接收成功 (显示正常气泡)
    ERROR    // 发送失败 (显示红色感叹号)
}
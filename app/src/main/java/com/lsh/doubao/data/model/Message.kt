package com.lsh.doubao.data.model

import java.util.UUID

/**
 * 聊天消息实体类
 *
 * 对应 API 中的 message 对象。
 * 为了适配 Android UI 展示和 Room 数据库存储，我们将 API 中复杂的嵌套结构
 * 简化为扁平的字段。
 */
data class Message(
    // 唯一标识符，用于 DiffUtil 对比和数据库主键
    val id: String = UUID.randomUUID().toString(),

    // 消息角色 (user / assistant)
    // 用于在构建"记忆"上下文时告诉 API 这句话是谁说的
    val role: MessageRole,

    // 消息正文内容
    // 对应 API 响应中的 content -> type="output_text" -> text
    // 或 API 请求中的 content -> type="input_text" -> text
    var content: String = "",

    // 深度思考/推理内容
    // 对应 API 响应中的 type="reasoning" -> summary -> text
    // 仿豆包 UI 时，这部分内容通常显示在折叠框里
    var reasoningContent: String? = null,

    // 图片 URL (可选)
    // 对应 API 请求中的 content -> type="input_image" -> image_url
    val imageUrl: String? = null,

    // 消息时间戳 (用于 UI 排序和显示时间)
    val timestamp: Long = System.currentTimeMillis(),

    // 消息状态 (用于 UI 显示 loading 动画或重发按钮)
    // 本地字段，不需要发送给 API
    var status: MessageStatus = MessageStatus.SENDING
)

/**
 * 角色枚举：定义消息是谁发的
 */
enum class MessageRole(val apiValue: String) {
    USER("user"),          // 用户
    ASSISTANT("assistant"), // AI
    SYSTEM("system");      // 系统预设 (可选，用于设置 AI 人设)

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
    SENDING, // 正在发送/正在生成 (显示转圈或打字机光标)
    SUCCESS, // 发送/接收成功 (显示正常气泡)
    ERROR    // 发送失败 (显示红色感叹号)
}
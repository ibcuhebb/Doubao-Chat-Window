package com.lsh.doubao.data.model

import com.google.gson.annotations.SerializedName

// 1. 发送给 API 的请求体
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true, // 开启流式输出
    val temperature: Double = 0.7
)

// 2. API 消息结构 (仅包含角色和内容)
data class ApiMessage(
    val role: String,
    val content: String
)

// 3. API 返回的流式响应片段
data class ChatResponse(
    val id: String?,
    val choices: List<ChatChoice>?
)

data class ChatChoice(
    val index: Int,
    // 流式模式下，变化的内容在 delta 字段中
    // 非流式模式下，完整内容在 message 字段中
    val delta: ApiMessage?,
    val message: ApiMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)
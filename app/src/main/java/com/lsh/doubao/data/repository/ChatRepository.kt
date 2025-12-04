package com.lsh.doubao.data.repository

import android.util.Log
import com.lsh.doubao.data.local.MessageDao
import com.lsh.doubao.data.model.ApiMessage
import com.lsh.doubao.data.model.ChatRequest
import com.lsh.doubao.data.model.ChatResponse
import com.lsh.doubao.data.model.Message
import com.lsh.doubao.data.model.MessageRole
import com.lsh.doubao.data.model.MessageStatus
import com.lsh.doubao.data.remote.api.ChatApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepository(
    private val apiService: ChatApiService,
    private val messageDao: MessageDao
) {

    // 对外暴露的消息流，ViewModel 只需要监听这个
    val allMessages: Flow<List<Message>> = messageDao.getAllMessages()

    private val gson = Gson()

    /**
     * 发送消息的核心逻辑
     * 包含：存库 -> 构建上下文 -> 发请求 -> 解析流 -> 更新库
     */
    suspend fun sendMessage(userContent: String) {
        withContext(Dispatchers.IO) {
            // 1. 保存用户消息到本地
            val userMsg = Message(
                role = MessageRole.USER,
                content = userContent,
                status = MessageStatus.SUCCESS
            )
            messageDao.insertMessage(userMsg)

            // 2. 预先创建一条空的 AI 消息占位 (状态为 SENDING)
            val aiMsgId = java.util.UUID.randomUUID().toString()
            var currentAiContent = "" // 用于累加流式返回的内容
            var currentReasoning = "" // 用于累加深度思考内容

            val initialAiMsg = Message(
                id = aiMsgId,
                role = MessageRole.ASSISTANT,
                content = "", // 一开始是空的
                reasoningContent = null,
                status = MessageStatus.SENDING
            )
            messageDao.insertMessage(initialAiMsg)

            try {
                // 3. 构建"记忆"上下文
                // 从数据库取最近 10 条，并反转顺序 (API 需要越旧的越靠前)
                val history = messageDao.getRecentMessages(10).reversed()

                // 将本地实体转为 API 实体
                val apiMessages = history.map { msg ->
                    ApiMessage(
                        role = msg.role.apiValue,
                        content = msg.content
                    )
                }.toMutableList()

                // 4. 构建 API 请求体
                val request = ChatRequest(
                    model = "doubao-seed-1-6-flash-250828",
                    messages = apiMessages,
                    stream = true
                )

                // 5. 发起网络请求
                val response = apiService.streamChat(request).execute()

                if (response.isSuccessful && response.body() != null) {
                    // 6. 处理流式响应 (SSE)
                    val source = response.body()!!.byteStream()
                    val reader = BufferedReader(InputStreamReader(source))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val originalLine = line ?: continue

                        // SSE 格式通常以 "data: " 开头
                        if (!originalLine.startsWith("data: ")) continue

                        // 去掉前缀
                        val json = originalLine.removePrefix("data: ").trim()

                        // 结束标志
                        if (json == "[DONE]") break

                        try {
                            // 解析 JSON
                            val chatResponse = gson.fromJson(json, ChatResponse::class.java)
                            val choice = chatResponse.choices?.firstOrNull()

                            // 获取增量内容 (Delta)
                            val deltaContent = choice?.delta?.content
                            val deltaReasoning = choice?.delta?.content // 注意：这里假设 reasoning 也在 content 字段，实际 DeepSeek/豆包可能有单独字段，此处暂且处理标准 content

                            if (!deltaContent.isNullOrEmpty()) {
                                currentAiContent += deltaContent

                                // 7. 实时更新数据库
                                // 注意：高频更新数据库可能会导致 UI 闪烁，实际生产中通常会做缓冲 (Buffer)
                                // 这里为了演示简单，直接更新
                                val updatedMsg = initialAiMsg.copy(
                                    content = currentAiContent,
                                    // reasoningContent = currentReasoning, // 如果有思考内容在这里更新
                                    status = MessageStatus.SENDING
                                )
                                messageDao.updateMessage(updatedMsg)
                            }
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Parse error: $json", e)
                        }
                    }

                    // 8. 接收完成，更新状态为 SUCCESS
                    val finalMsg = initialAiMsg.copy(
                        content = currentAiContent,
                        status = MessageStatus.SUCCESS
                    )
                    messageDao.updateMessage(finalMsg)

                } else {
                    // 请求失败
                    val errorMsg = initialAiMsg.copy(
                        content = "服务器连接失败: ${response.code()}",
                        status = MessageStatus.ERROR
                    )
                    messageDao.updateMessage(errorMsg)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // 异常处理
                val errorMsg = initialAiMsg.copy(
                    content = "发送失败: ${e.message}",
                    status = MessageStatus.ERROR
                )
                messageDao.updateMessage(errorMsg)
            }
        }
    }
}
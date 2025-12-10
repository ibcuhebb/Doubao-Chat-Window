package com.lsh.doubao.data.repository

import android.content.Context
import android.util.Log
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import com.lsh.doubao.data.local.MessageDao
import com.lsh.doubao.data.local.engine.LocalModelManager
import com.lsh.doubao.data.model.ApiMessage
import com.lsh.doubao.data.model.ChatRequest
import com.lsh.doubao.data.model.ChatResponse
import com.lsh.doubao.data.model.Message
import com.lsh.doubao.data.model.MessageRole
import com.lsh.doubao.data.model.MessageStatus
import com.lsh.doubao.data.remote.api.ChatApiService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors

class ChatRepository(
    private val context: Context,
    private val apiService: ChatApiService,
    private val messageDao: MessageDao
) {

    val allMessages: Flow<List<Message>> = messageDao.getAllMessages()
    private val gson = Gson()

    // === 1. 严格复刻 AppViewModel 的线程策略 ===
    // 专门用于处理引擎状态同步和历史记录更新的单线程执行器
    private val executorService = Executors.newSingleThreadExecutor()

    // 用于执行流式生成的协程作用域（IO 线程，避免阻塞主线程）
    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())

    // 引擎实例 (复用 MLCChat 的 Engine 逻辑)
    private val mlcEngine = MLCEngine()
    private var currentLoadedModelId: String? = null
    private val localModelManager = LocalModelManager.getInstance(context)

    // 维护历史记录（对应 AppViewModel 中的 historyMessages）
    private var historyMessages = mutableListOf<OpenAIProtocol.ChatCompletionMessage>()

    /**
     * 加载模型（通过 Executor 串行化执行）
     */
    fun loadModel(modelId: String, callback: (Boolean) -> Unit) {
        executorService.submit {
            try {
                if (currentLoadedModelId == modelId) {
                    callback(true)
                    return@submit
                }

                val modelState = localModelManager.modelListState.value.find { it.modelConfig.modelId == modelId }
                if (modelState == null || !modelState.isReady()) {
                    callback(false)
                    return@submit
                }

                // 卸载旧模型并加载新模型
                mlcEngine.unload()
                mlcEngine.reload(modelState.modelDirFile.absolutePath, modelState.modelConfig.modelLib)
                mlcEngine.reset()

                currentLoadedModelId = modelId
                historyMessages.clear() // 切换模型时清空上下文历史
                callback(true)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }

    /**
     * 发送消息
     */
    suspend fun sendMessage(userContent: String, modelId: String) {
        // 1. 立即保存用户消息到数据库
        val userMsg = Message(role = MessageRole.USER, content = userContent, status = MessageStatus.SUCCESS)
        messageDao.insertMessage(userMsg)

        // 2. 预创建 AI 消息
        val aiMsgId = UUID.randomUUID().toString()
        val initialAiMsg = Message(id = aiMsgId, role = MessageRole.ASSISTANT, content = "", status = MessageStatus.SENDING)
        messageDao.insertMessage(initialAiMsg)

        if (isRemoteModel(modelId)) {
            streamRemoteChat(userContent, initialAiMsg)
        } else {
            // === 3. 本地模型处理：提交到 Executor 准备数据，然后在协程中生成 ===
            executorService.submit {
                // 在单线程池中更新历史记录，保证线程安全
                historyMessages.add(OpenAIProtocol.ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.user,
                    content = userContent
                ))

                // 启动协程进行推理（跳出 Executor，避免阻塞它）
                // 对应 AppViewModel.kt 中的 viewModelScope.launch
                repositoryScope.launch {
                    streamLocalChatInScope(initialAiMsg)
                }
            }
        }
    }

    private fun isRemoteModel(modelId: String): Boolean {
        return modelId == "doubao-seed-1-6-flash-250828" || !modelId.contains("MLC")
    }

    /**
     * 核心推理逻辑 (运行在 repositoryScope 中)
     */
    private suspend fun streamLocalChatInScope(initialAiMsg: Message) {
        try {
            // 调用引擎生成 (挂起函数)
            // 对应 AppViewModel.kt 中的 engine.chat.completions.create
            val responses = mlcEngine.chat.completions.create(
                messages = historyMessages,
                stream = true,
                stream_options = OpenAIProtocol.StreamOptions(include_usage = true)
            )

            var currentContent = ""
            var lastDbUpdateTime = 0L
            val DB_UPDATE_THRESHOLD = 2000L // 2秒缓冲，防止数据库写入造成卡顿

            for (res in responses) {
                // 只要引擎返回数据，就立即处理
                for (choice in res.choices) {
                    val deltaText = choice.delta.content?.asText()
                    if (!deltaText.isNullOrEmpty()) {
                        currentContent += deltaText

                        // 缓冲写入数据库
                        val now = System.currentTimeMillis()
                        if (now - lastDbUpdateTime > DB_UPDATE_THRESHOLD) {
                            messageDao.updateMessage(initialAiMsg.copy(content = currentContent, status = MessageStatus.SENDING))
                            lastDbUpdateTime = now
                        }
                    }
                }
            }

            // 生成完成，更新历史记录上下文（为了下一轮对话）
            // 注意：再次提交到 executorService 以保证 historyMessages 的线程安全
            val finalContent = currentContent
            executorService.submit {
                if (finalContent.isNotEmpty()) {
                    historyMessages.add(OpenAIProtocol.ChatCompletionMessage(
                        role = OpenAIProtocol.ChatCompletionRole.assistant,
                        content = finalContent
                    ))
                }
            }

            // 最终更新数据库状态为 SUCCESS
            messageDao.updateMessage(initialAiMsg.copy(content = currentContent, status = MessageStatus.SUCCESS))

        } catch (e: Exception) {
            e.printStackTrace()
            messageDao.updateMessage(initialAiMsg.copy(content = "生成失败: ${e.message}", status = MessageStatus.ERROR))
        }
    }

    // ... streamRemoteChat 保持不变 ...
    private suspend fun streamRemoteChat(userContent: String, initialMsg: Message) {
        // (保持你原有的远端调用代码)
        // ...
        val history = messageDao.getRecentMessages(10).reversed()
        val apiMessages = history.map { msg ->
            ApiMessage(role = msg.role.apiValue, content = msg.content)
        }.toMutableList()

        val request = ChatRequest(
            model = "doubao-seed-1-6-flash-250828",
            messages = apiMessages,
            stream = true
        )
        try {
            val response = apiService.streamChat(request).execute()
            if (response.isSuccessful && response.body() != null) {
                val source = response.body()!!.byteStream()
                val reader = BufferedReader(InputStreamReader(source))
                var line: String?
                var currentContent = ""
                while (reader.readLine().also { line = it } != null) {
                    val originalLine = line ?: continue
                    if (!originalLine.startsWith("data: ")) continue
                    val json = originalLine.removePrefix("data: ").trim()
                    if (json == "[DONE]") break
                    try {
                        val chatResponse = gson.fromJson(json, ChatResponse::class.java)
                        val deltaContent = chatResponse.choices?.firstOrNull()?.delta?.content
                        if (!deltaContent.isNullOrEmpty()) {
                            currentContent += deltaContent
                            messageDao.updateMessage(initialMsg.copy(content = currentContent, status = MessageStatus.SENDING))
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                messageDao.updateMessage(initialMsg.copy(content = currentContent, status = MessageStatus.SUCCESS))
            } else {
                messageDao.updateMessage(initialMsg.copy(content = "Error: ${response.code()}", status = MessageStatus.ERROR))
            }
        } catch (e: Exception) {
            messageDao.updateMessage(initialMsg.copy(content = "Fail: ${e.message}", status = MessageStatus.ERROR))
        }
    }
}
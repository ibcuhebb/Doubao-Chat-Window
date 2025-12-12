package com.lsh.doubao.data.repository

import android.content.Context
import android.util.Log
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import com.lsh.doubao.data.local.AppDatabase
import com.lsh.doubao.data.local.MessageDao
import com.lsh.doubao.data.local.engine.LocalModelManager
import com.lsh.doubao.data.model.*
import com.lsh.doubao.data.remote.RetrofitClient
import com.lsh.doubao.data.remote.api.ChatApiService
import com.google.gson.Gson
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors

class ChatRepository private constructor(
    private val context: Context,
    private val apiService: ChatApiService,
    private val messageDao: MessageDao
) {
    // 单例模式
    companion object {
        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getDatabase(context)
                val apiService = RetrofitClient.apiService
                INSTANCE ?: ChatRepository(context.applicationContext, apiService, database.messageDao()).also { INSTANCE = it }
            }
        }
    }

    val remoteMessages: Flow<List<Message>> = messageDao.getAllMessages()
    private val _localMessages = MutableStateFlow<List<Message>>(emptyList())
    val localMessages: Flow<List<Message>> = _localMessages.asStateFlow()
    private val localMessageList = mutableListOf<Message>()
    private val engineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val stateMutex = Mutex()
    private val mlcEngine = MLCEngine()
    private val localModelManager = LocalModelManager.getInstance(context)
    private var currentLoadedModelId: String? = null
    private var historyMessages = mutableListOf<OpenAIProtocol.ChatCompletionMessage>()
    private val gson = Gson()

    /**
     * 加载模型
     */
    fun loadModel(modelId: String, callback: (Boolean) -> Unit) {
        // 使用 engineDispatcher 启动，后台线程
        CoroutineScope(engineDispatcher).launch {
            try {
                stateMutex.withLock {
                    if (currentLoadedModelId != modelId) {
                        val modelState = localModelManager.modelListState.value.find { it.modelConfig.modelId == modelId }
                        if (modelState != null && modelState.isReady()) {
                            mlcEngine.unload()
                            mlcEngine.reload(modelState.modelDirFile.absolutePath, modelState.modelConfig.modelLib)
                            mlcEngine.reset()
                            currentLoadedModelId = modelId
                            historyMessages.clear()
                            localMessageList.clear()
                            _localMessages.value = emptyList()
                        }
                    }
                }
                withContext(Dispatchers.Main) { callback(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { callback(false) }
            }
        }
    }

    // 本地消息发送
    suspend fun sendLocalMessage(userContent: String) {
        val userMsg = Message(id = UUID.randomUUID().toString(), role = MessageRole.USER, content = userContent, status = MessageStatus.SUCCESS)
        updateLocalList { add(userMsg) }
        val aiMsgId = UUID.randomUUID().toString()
        val initialAiMsg = Message(id = aiMsgId, role = MessageRole.ASSISTANT, content = "...", status = MessageStatus.SENDING)
        updateLocalList { add(initialAiMsg) }
        withContext(engineDispatcher) {
            streamLocalChat(userContent, aiMsgId)
        }
    }

    // 更新本地模型配置
    private suspend fun updateLocalList(action: MutableList<Message>.() -> Unit) {
        stateMutex.withLock {
            localMessageList.action()
            _localMessages.value = ArrayList(localMessageList)
        }
    }

    // 本地对话流式生成
    private suspend fun streamLocalChat(userContent: String, aiMsgId: String) {
        try {
            stateMutex.withLock {
                historyMessages.add(OpenAIProtocol.ChatCompletionMessage(role = OpenAIProtocol.ChatCompletionRole.user, content = userContent))
            }
            val requestMessages = ArrayList(historyMessages)

            val responses = mlcEngine.chat.completions.create(
                messages = requestMessages,
                max_tokens = 8192,
                stream = true
            )

            val fullContentBuilder = StringBuilder()

            for (res in responses) {
                for (choice in res.choices) {
                    val deltaText = choice.delta.content?.asText() ?: ""
                    if (deltaText.isNotEmpty()) {
                        fullContentBuilder.append(deltaText)
                        stateMutex.withLock {
                            val index = localMessageList.indexOfFirst { it.id == aiMsgId }
                            if (index != -1) {
                                localMessageList[index] = localMessageList[index].copy(content = fullContentBuilder.toString(), status = MessageStatus.SENDING)
                                _localMessages.value = ArrayList(localMessageList)
                            }
                        }
                    }
                }
            }

            val finalContent = fullContentBuilder.toString()
            stateMutex.withLock {
                val index = localMessageList.indexOfFirst { it.id == aiMsgId }
                if (index != -1) {
                    localMessageList[index] = localMessageList[index].copy(content = finalContent, status = MessageStatus.SUCCESS)
                    _localMessages.value = ArrayList(localMessageList)
                }
                historyMessages.add(OpenAIProtocol.ChatCompletionMessage(role = OpenAIProtocol.ChatCompletionRole.assistant, content = finalContent))
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Local Error", e)
            stateMutex.withLock {
                val index = localMessageList.indexOfFirst { it.id == aiMsgId }
                if (index != -1) {
                    localMessageList[index] = localMessageList[index].copy(content = "Error: ${e.message}", status = MessageStatus.ERROR)
                    _localMessages.value = ArrayList(localMessageList)
                }
            }
        }
    }


    // 远端发送消息
    suspend fun sendRemoteMessage(userContent: String) {
        // 数据库操作和网络请求，全部放入 IO 线程
        withContext(Dispatchers.IO) {
            val userMsg = Message(role = MessageRole.USER, content = userContent, status = MessageStatus.SUCCESS)
            messageDao.insertMessage(userMsg)

            val initialAiMsg = Message(id = UUID.randomUUID().toString(), role = MessageRole.ASSISTANT, content = "", status = MessageStatus.SENDING)
            messageDao.insertMessage(initialAiMsg)

            streamRemoteChat(userContent, initialAiMsg)
        }
    }

    // 远端对话流式生成
    private suspend fun streamRemoteChat(userContent: String, initialMsg: Message) {

        val history = messageDao.getRecentMessages(10).reversed()
        val apiMessages = history.map { msg ->
            ApiMessage(role = msg.role.apiValue, content = msg.content)
        }.toMutableList()

        val request = ChatRequest(model = "doubao-seed-1-6-flash-250828", messages = apiMessages, stream = true)

        try {
            // execute() 是同步阻塞网络请求，必须在后台线程执行
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
            e.printStackTrace()
            // 这里的 e.message 会捕获 NetworkOnMainThreadException
            messageDao.updateMessage(initialMsg.copy(content = "Fail: ${e.message}", status = MessageStatus.ERROR))
        }
    }
}
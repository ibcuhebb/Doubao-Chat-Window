package com.lsh.doubao.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lsh.doubao.data.model.Message
import com.lsh.doubao.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    // === 关键修改 1: 只观察远端消息 (remoteMessages) ===
    // 因为 ChatActivity 现在只负责显示数据库里的历史记录和远端对话
    val messages: StateFlow<List<Message>> = repository.remoteMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // === 关键修改 2: sendMessage 只调用 sendRemoteMessage ===
    fun sendMessage(content: String, modelId: String) {
        if (content.isBlank()) return

        // 注意：这里我们不再处理本地模型，因为如果用户选了本地模型，
        // 在 handleModelSelection 里就已经跳转到 LocalChatActivity 了。
        // 留在这里的逻辑一定是远端对话。

        viewModelScope.launch {
            repository.sendRemoteMessage(content)
        }
    }

    // 切换模型逻辑保持不变，用于下载和预加载
    fun switchModel(modelId: String, onSuccess: () -> Unit, onError: () -> Unit) {
        repository.loadModel(modelId) { success ->
            if (success) onSuccess() else onError()
        }
    }

    class Factory(private val repository: ChatRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
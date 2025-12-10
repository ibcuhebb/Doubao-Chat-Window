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

    val messages: StateFlow<List<Message>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 修改：增加 modelId 参数
    fun sendMessage(content: String, modelId: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            // 将 modelId 传给 repository
            repository.sendMessage(content, modelId)
        }
    }

    fun switchModel(modelId: String, onSuccess: () -> Unit, onError: () -> Unit) {
        if (modelId == "doubao-seed-1-6-flash-250828") {
            onSuccess()
            return
        }

        // Repository 现在负责线程调度，这里不需要 launch(Dispatchers.IO)
        repository.loadModel(modelId) { success ->
            viewModelScope.launch { // 回到主线程更新 UI
                if (success) onSuccess() else onError()
            }
        }
    }

    class Factory(private val repository: ChatRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
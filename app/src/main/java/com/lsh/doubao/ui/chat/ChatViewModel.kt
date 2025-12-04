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

    // 将 Repository 中的 Flow (来自 Room) 转换为 UI 可观察的 StateFlow
    // stateIn 操作符可以把冷流变成热流，确保 Activity 旋转时数据不丢失
    val messages: StateFlow<List<Message>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // App 切到后台 5秒后停止更新数据库流
            initialValue = emptyList()
        )

    // 发送消息
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // 启动协程调用 Repository
        viewModelScope.launch {
            // 这里不需要再手动更新 UI 列表了
            // 因为 Repository 会把消息存入数据库
            // Room 会自动通知上面的 messages Flow 更新
            repository.sendMessage(content)
        }
    }

    // 定义工厂类，用于构建带参数的 ViewModel
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
package com.lsh.doubao.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsh.doubao.data.model.Message
import com.lsh.doubao.data.model.MessageRole
import com.lsh.doubao.data.model.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // 消息列表状态 (使用 StateFlow 通知 UI 更新)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        // Day 1: 添加一条欢迎语，方便看效果
        addMessage(
            Message(
                role = MessageRole.ASSISTANT,
                content = "你好！我是你的 AI 助手。你可以问我任何问题，或者让我帮你写代码、写文案。",
                reasoningContent = null // 欢迎语通常没有深度思考
            )
        )
    }

    // 发送消息
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // 1. 添加用户消息到列表
        val userMsg = Message(
            role = MessageRole.USER,
            content = content,
            status = MessageStatus.SENDING
        )
        addMessage(userMsg)

        // Day 1 模拟: 假装 AI 在思考，1秒后回复
        // (Day 2 会替换为真实网络请求)
        viewModelScope.launch {
            delay(1000)
            // 模拟回复
            val aiMsg = Message(
                role = MessageRole.ASSISTANT,
                content = "收到！你刚才发送了：$content\n(这是 Day 1 的模拟回复，Day 2 我们将接入真实大模型)",
                reasoningContent = "用户发送了一条测试消息，我需要确认收到并说明当前是测试模式。"
            )
            addMessage(aiMsg)
        }
    }

    // 辅助方法：向列表添加消息
    private fun addMessage(msg: Message) {
        val currentList = _messages.value.toMutableList()
        currentList.add(msg)
        _messages.value = currentList
    }
}
package com.lsh.doubao.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lsh.doubao.R
import com.lsh.doubao.ui.chat.adapter.ChatAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    // UI 组件引用
    private lateinit var rvChatList: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var layoutInputIdle: LinearLayout
    private lateinit var layoutInputActions: FrameLayout
    private lateinit var ivBack: View
    private lateinit var ivCamera: View // 新增引用

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initViews()
        setupRecyclerView()
        setupInputListener()
        observeViewModel()
    }

    private fun initViews() {
        rvChatList = findViewById(R.id.rv_chat_list)
        etInput = findViewById(R.id.et_input)
        btnSend = findViewById(R.id.btn_send)
        layoutInputIdle = findViewById(R.id.layout_input_idle)
        layoutInputActions = findViewById(R.id.layout_input_actions)
        ivBack = findViewById(R.id.iv_back)
        ivCamera = findViewById(R.id.iv_camera) // 初始化相机图标

        // 简单的返回按钮逻辑
        ivBack.setOnClickListener { finish() }

        // 发送按钮点击
        btnSend.setOnClickListener {
            val content = etInput.text.toString()
            viewModel.sendMessage(content)
            etInput.setText("") // 清空输入框
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        rvChatList.adapter = adapter
        // 修改 1: 移除 stackFromEnd = true，让消息从顶部开始排列
        rvChatList.layoutManager = LinearLayoutManager(this)
    }

    private fun setupInputListener() {
        // 监听输入框变化，切换图标状态
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 只要文字不为空（包括空格），就认为是输入状态
                val hasText = !s.isNullOrEmpty()
                updateInputState(hasText)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // 切换输入状态 (空闲 vs 输入中)
    private fun updateInputState(isTyping: Boolean) {
        if (isTyping) {
            // --- 输入状态 ---

            // 1. 隐藏左侧相机 (ConstraintLayout 会自动让输入框向左扩展)
            if (ivCamera.visibility != View.GONE) {
                ivCamera.visibility = View.GONE
            }

            // 2. 隐藏右侧空闲图标 (语音/加号)
            if (layoutInputIdle.visibility != View.GONE) {
                layoutInputIdle.visibility = View.GONE
            }

            // 3. 显示发送按钮
            // 强制显示，防止 FAB 有时 hide 后无法 show 的问题
            if (btnSend.visibility != View.VISIBLE) {
                btnSend.visibility = View.VISIBLE
                btnSend.show()
            }
        } else {
            // --- 空闲状态 ---

            // 1. 显示左侧相机
            if (ivCamera.visibility != View.VISIBLE) {
                ivCamera.visibility = View.VISIBLE
            }

            // 2. 显示右侧空闲图标
            if (layoutInputIdle.visibility != View.VISIBLE) {
                layoutInputIdle.visibility = View.VISIBLE
            }

            // 3. 隐藏发送按钮
            if (btnSend.visibility != View.GONE) {
                //btnSend.hide()
                btnSend.visibility = View.GONE
            }
        }
    }

    private fun observeViewModel() {
        // 监听消息列表变化
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages) {
                    // 数据更新完成后，滚动到底部
                    if (messages.isNotEmpty()) {
                        rvChatList.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }
}
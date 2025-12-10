package com.lsh.doubao.ui.chat

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lsh.doubao.R
import com.lsh.doubao.data.local.AppDatabase
import com.lsh.doubao.data.local.engine.LocalModelManager
import com.lsh.doubao.data.remote.RetrofitClient
import com.lsh.doubao.data.repository.ChatRepository
import com.lsh.doubao.ui.chat.adapter.ChatAdapter
import com.lsh.doubao.ui.chat.adapter.ModelAdapter
import com.lsh.doubao.ui.chat.model.ModelUiBean
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val messageDao = database.messageDao()
        val apiService = RetrofitClient.apiService
        val repository = ChatRepository(applicationContext, apiService, messageDao)
        ChatViewModel.Factory(repository)
    }

    private lateinit var adapter: ChatAdapter
    private lateinit var rvChatList: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var layoutInputIdle: LinearLayout
    private lateinit var layoutInputActions: FrameLayout
    private lateinit var ivBack: View
    private lateinit var ivCamera: View
    private lateinit var btnModelSwitch: LinearLayout
    private lateinit var tvCurrentModel: TextView

    private var currentModelId = "doubao-seed-1-6-flash-250828"
    private var popupWindow: PopupWindow? = null

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
        ivCamera = findViewById(R.id.iv_camera)
        btnModelSwitch = findViewById(R.id.btn_model_switch)
        tvCurrentModel = findViewById(R.id.tv_current_model)

        ivBack.setOnClickListener { finish() }

        // 点击打开精美的模型列表
        btnModelSwitch.setOnClickListener { view ->
            showModelSelectionPopup(view)
        }

        btnSend.setOnClickListener {
            val content = etInput.text.toString()
            viewModel.sendMessage(content, currentModelId)
            etInput.setText("")
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        rvChatList.adapter = adapter
        rvChatList.layoutManager = LinearLayoutManager(this)
    }

    // === 新增：使用 Popup + RecyclerView 显示模型列表 ===
    private fun showModelSelectionPopup(anchorView: View) {
        // 1. 加载 popup_model_list.xml
        val contentView = LayoutInflater.from(this).inflate(R.layout.popup_model_list, null)
        val rvModelList = contentView.findViewById<RecyclerView>(R.id.rv_model_list)

        // 2. 准备数据
        val localManager = LocalModelManager.getInstance(this)
        val localModels = localManager.modelListState.value

        val uiList = mutableListOf<ModelUiBean>()
        // 远端模型
        uiList.add(
            ModelUiBean(
                "doubao-seed-1-6-flash-250828",
                "Doubao-Remote",
                false
            )
        )
        // 本地模型
        localModels.forEach { state ->
            uiList.add(
                ModelUiBean(
                    state.modelConfig.modelId,
                    state.modelConfig.modelId,
                    true,
                    state.modelConfig.modelLib
                )
            )
        }

        // 3. 设置 Adapter
        val modelAdapter = ModelAdapter { selectedModel ->
            handleModelSelection(selectedModel, localModels)
            popupWindow?.dismiss()
        }
        modelAdapter.submitList(uiList)
        modelAdapter.currentModelId = currentModelId // 设置当前选中项

        rvModelList.layoutManager = LinearLayoutManager(this)
        rvModelList.adapter = modelAdapter

        // 4. 显示 Popup
        popupWindow = PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow?.elevation = 10f
        popupWindow?.showAsDropDown(anchorView, 0, -800) // 根据实际高度调整偏移
    }

    private fun handleModelSelection(model: ModelUiBean, localModels: List<LocalModelManager.ModelState>) {
        // 1. 如果选中的是当前模型，直接返回
        if (currentModelId == model.id) return

        if (model.isLocal) {
            val modelState = localModels.find { it.modelConfig.modelId == model.id }
            // 2. 如果模型还没下载，走原来的下载流程
            if (modelState != null && !modelState.isReady()) {
                currentModelId = model.id
                tvCurrentModel.text = model.displayName
                modelState.handleStart()
                showDownloadProgressDialog(modelState)
                return
            }
        }

        // 3. 显示“正在加载模型...”的弹窗 (禁止用户操作)
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("正在加载模型")
            .setMessage("正在初始化引擎，请稍候（首次加载可能较慢）...")
            .setCancelable(false) // 禁止点击外部关闭
            .create()
        loadingDialog.show()

        // 4. 调用 ViewModel 切换模型
        viewModel.switchModel(
            modelId = model.id,
            onSuccess = {
                loadingDialog.dismiss()
                currentModelId = model.id
                tvCurrentModel.text = model.displayName
                // 刷新列表 UI 中的对勾
                // (如果在 Adapter 外部持有 adapter 引用，可以在这里 notifyDataSetChanged)
                // 比如: (rvChatList.adapter as? ModelAdapter)?.currentModelId = model.id

                Toast.makeText(this, "模型加载完毕，可以对话了", Toast.LENGTH_SHORT).show()
            },
            onError = {
                loadingDialog.dismiss()
                Toast.makeText(this, "模型加载失败，请重试", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // === 新增：显示下载进度弹窗 (使用 dialog_download_progress.xml) ===
    private fun showDownloadProgressDialog(modelState: LocalModelManager.ModelState) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_download_progress, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_progress_title)
        val pbDownload = dialogView.findViewById<ProgressBar>(R.id.pb_download)
        val tvPercent = dialogView.findViewById<TextView>(R.id.tv_progress_percent)

        tvTitle.text = "正在下载 ${modelState.modelConfig.modelId}..."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // 禁止点击外部关闭，强制等待或手动暂停（暂未实现暂停按钮）
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        // 监听下载进度
        lifecycleScope.launch {
            // 这是一个简单的轮询或组合流监听，根据 LocalModelManager 的实现
            // 这里假设 modelState.progress 和 total 是 StateFlow
            // 我们组合监听它们

            // 注意：因为 Kotlin Flow 合并比较繁琐，这里简化处理：开启一个循环检查或者分别监听
            // 更优雅的方式是在 ModelState 里提供一个 computed flow (percent)
            // 这里为了简单，我们启动一个协程每 100ms 刷新一次 UI，或者分别监听

            val job = launch {
                modelState.progress.collect { current ->
                    val total = modelState.total.value
                    if (total > 0) {
                        val percent = (current.toFloat() / total.toFloat() * 100).toInt()
                        pbDownload.progress = percent
                        tvPercent.text = "$percent%"

                        // 简单的完成判断 (实际应该看 InitState)
                        if (current >= total && total > 1) {
                            // 下载可能包含 tokenizer 和 weights，简单的数量对比
                        }
                    }
                }
            }

            // 监听状态变化来关闭弹窗
            modelState.initState.collect { state ->
                if (state == com.lsh.doubao.data.model.ModelInitState.Finished) {
                    dialog.dismiss()
                    job.cancel()
                    Toast.makeText(this@ChatActivity, "模型下载完成，可以对话了！", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupInputListener() {
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                updateInputState(hasText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateInputState(isTyping: Boolean) {
        if (isTyping) {
            ivCamera.visibility = View.GONE
            layoutInputIdle.visibility = View.GONE
            if (btnSend.visibility != View.VISIBLE) {
                btnSend.visibility = View.VISIBLE
                btnSend.show()
            }
        } else {
            ivCamera.visibility = View.VISIBLE
            layoutInputIdle.visibility = View.VISIBLE
            if (btnSend.visibility != View.GONE) {
                btnSend.visibility = View.GONE
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        rvChatList.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }
}
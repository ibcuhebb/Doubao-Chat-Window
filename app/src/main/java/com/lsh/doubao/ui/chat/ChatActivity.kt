package com.lsh.doubao.ui.chat

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
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
import com.lsh.doubao.data.local.engine.LocalModelManager
import com.lsh.doubao.data.repository.ChatRepository
import com.lsh.doubao.ui.chat.adapter.ChatAdapter
import com.lsh.doubao.ui.chat.adapter.ModelAdapter
import com.lsh.doubao.ui.chat.model.ModelUiBean
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels {
        // 直接使用单例 getInstance
        val repository = ChatRepository.getInstance(applicationContext)
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

        // 点击打开模型列表
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

    // 使用 Popup + RecyclerView 显示模型列表
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
                "doubao-remote",
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
        // 获取屏幕高度
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        // 设置 Popup 高度
        popupWindow = PopupWindow(contentView,
            (screenWidth * 0.8).toInt(),
            (screenHeight * 0.4).toInt(),
            true
        )
        popupWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow?.elevation = 20f

        // 居中显示
        popupWindow?.showAtLocation(anchorView.rootView, android.view.Gravity.CENTER, 0, 0)
    }

    private fun handleModelSelection(model: ModelUiBean, localModels: List<LocalModelManager.ModelState>) {
        if (currentModelId == model.id) return

        if (model.isLocal) {
            val modelState = localModels.find { it.modelConfig.modelId == model.id }
            // 如果模型还没下载，走下载流程
            if (modelState != null && !modelState.isReady()) {
                currentModelId = model.id
                tvCurrentModel.text = model.displayName
                modelState.handleStart()
                showDownloadProgressDialog(modelState)
                return
            }
        }

        // 模型已就绪（或是远端模型），直接调用我们封装好的加载函数
        loadAndSwitchModel(model.id, model.isLocal, model.displayName)
    }

    // 显示下载进度弹窗
    private fun showDownloadProgressDialog(modelState: LocalModelManager.ModelState) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_download_progress, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_progress_title)
        val pbDownload = dialogView.findViewById<ProgressBar>(R.id.pb_download)
        val tvPercent = dialogView.findViewById<TextView>(R.id.tv_progress_percent)

        tvTitle.text = "正在下载 ${modelState.modelConfig.modelId}..."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // 禁止点击外部关闭，强制等待
            .create()
        dialog.show()

        // 监听下载进度
        lifecycleScope.launch {

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
            lifecycleScope.launch {

                modelState.initState.collect { state ->
                    // 当状态变为 Finished (下载完成)
                    if (state == com.lsh.doubao.data.model.ModelInitState.Finished) {
                        dialog.dismiss()
                        // job.cancel()

                        Toast.makeText(this@ChatActivity, "下载完成，正在加载...", Toast.LENGTH_SHORT).show()

                        // 下载完成后，自动调用加载和跳转逻辑
                        loadAndSwitchModel(
                            modelId = modelState.modelConfig.modelId,
                            isLocal = true,
                            displayName = modelState.modelConfig.modelId
                        )
                    }
                }
            }
        }
    }

    // 统一处理模型加载与跳转
    private fun loadAndSwitchModel(modelId: String, isLocal: Boolean, displayName: String) {
        // 1. 显示加载弹窗
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle(if (isLocal) "正在加载本地引擎" else "正在切换模型")
            .setMessage(if (isLocal) "即将进入高性能对话模式..." else "请稍候...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // 2. 调用 ViewModel 切换
        viewModel.switchModel(
            modelId = modelId,
            onSuccess = {
                loadingDialog.dismiss()
                if (isLocal) {
                    // 如果是本地模型，跳转到第二界面
                    val intent = android.content.Intent(this, LocalChatActivity::class.java)
                    intent.putExtra("MODEL_ID", modelId)
                    startActivity(intent)
                } else {
                    // 如果是远端模型，留在当前界面并更新UI
                    currentModelId = modelId
                    tvCurrentModel.text = displayName
                    Toast.makeText(this, "已切换到 $displayName", Toast.LENGTH_SHORT).show()
                }
            },
            onError = {
                loadingDialog.dismiss()
                Toast.makeText(this, "模型加载失败", Toast.LENGTH_SHORT).show()
            }
        )
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
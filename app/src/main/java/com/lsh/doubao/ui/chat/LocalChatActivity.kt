package com.lsh.doubao.ui.chat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsh.doubao.R
import com.lsh.doubao.data.model.Message
import com.lsh.doubao.data.model.MessageRole
import com.lsh.doubao.data.model.MessageStatus
import com.lsh.doubao.data.repository.ChatRepository
import kotlinx.coroutines.launch

class LocalChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelId = intent.getStringExtra("MODEL_ID") ?: "Local Model"

        setContent {
            MaterialTheme {
                LocalChatRoute(
                    modelName = modelId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun LocalChatRoute(
    modelName: String,
    onBackClick: () -> Unit,
    repository: ChatRepository = ChatRepository.getInstance(LocalContext.current)
) {
    val messages by repository.localMessages.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LocalChatScreen(
        modelName = modelName,
        messages = messages,
        onBackClick = onBackClick,
        onSendMessage = { text ->
            scope.launch {
                repository.sendLocalMessage(text)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalChatScreen(
    modelName: String,
    messages: List<Message>,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "豆包 (本地模式)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = modelName, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            BottomInputBar(onSendMessage = onSendMessage)
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F7F7)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { message ->
                MessageItem(message)
            }
        }
    }
}

// 1. 消息气泡组件
@Composable
fun MessageItem(message: Message) {
    val isUser = message.role == MessageRole.USER
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // 外层容器：控制左右对齐
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // 气泡本体
        Surface(
            shape = if (isUser)
                RoundedCornerShape(15.dp, 15.dp, 15.dp, 15.dp)
            else
                RoundedCornerShape(15.dp, 15.dp, 15.dp, 15.dp),
            color = if (isUser) Color(0xFF3D5CFF) else Color(0xFFF2F4F5),
            modifier = Modifier.widthIn(max = 300.dp), // 限制最大宽度
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 1. 消息文本
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else Color(0xFF1F1F1F),
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )

                // 2. AI 消息的操作栏 (在气泡内，带分割线)
                if (!isUser) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // 分割线 (淡灰色)
                    HorizontalDivider(
                        color = Color(0xFFF0F0F0),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 操作按钮行：复制 -> 收藏 -> 点赞
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        //horizontalArrangement = Arrangement.SpaceBetween, // 均匀分布
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 复制
                        var isCopied by remember { mutableStateOf(false) }
                        ActionIconWithText(
                            iconRes = if (isCopied) R.drawable.ic_success else R.drawable.ic_copy,
                            text = "复制",
                            tint = if (isCopied) Color(0xFF4CAF50) else Color.Gray,
                            onClick = {
                                isCopied = !isCopied
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 2. 收藏
                        var isCollected by remember { mutableStateOf(false) }
                        ActionIconWithText(
                            iconRes = if (isCollected) R.drawable.ic_success else R.drawable.ic_bookmark,
                            text = if (isCollected) "已收藏" else "收藏",
                            tint = if (isCollected) Color(0xFF4CAF50) else Color.Gray,
                            onClick = {
                                isCollected = !isCollected
                                Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 3. 点赞
                        var isLiked by remember { mutableStateOf(false) }
                        ActionIconWithText(
                            iconRes = if (isLiked) R.drawable.ic_success else R.drawable.ic_thumb_up,
                            text = if (isLiked) "已赞" else "点赞",
                            tint = if (isLiked) Color(0xFF4CAF50) else Color.Gray,
                            onClick = {
                                isLiked = !isLiked
                                Toast.makeText(context, "已点赞", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

// 辅助组件：带文字的小图标
@Composable
fun ActionIconWithText(
    iconRes: Int,
    text: String,
    tint: Color = Color.Gray,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(12.dp), // 增加点击区域
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = text,
            modifier = Modifier.size(16.dp), // 图标稍微调小一点，显得精致
            tint = tint
        )
        //Spacer(modifier = Modifier.width(4.dp))
        //Text(text = text, fontSize = 12.sp, color = tint)
    }
}

// 2. 底部输入栏
@Composable
fun BottomInputBar(onSendMessage: (String) -> Unit) {
    var inputText by remember { mutableStateOf("") }
    val isTyping = inputText.isNotEmpty()

    // 悬浮卡片容器
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 20.dp),
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 4.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 4.dp) // 内部间距
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：相机
            AnimatedVisibility(
                visible = !isTyping,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                IconButton(onClick = { /* 打开相机 */ }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_camera),
                        contentDescription = "Camera",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 中间：输入框 (灰色圆角背景)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 0.dp)
                    .background(Color(0xFFFFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 10.dp), // 文字内边距
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text("发送消息或按住说话...", color = Color.Gray, fontSize = 15.sp)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(fontSize = 15.sp, color = Color.Black),
                    cursorBrush = SolidColor(Color(0xFF3D5CFF)),
                    maxLines = 4
                )
            }

            // 右侧：语音 & 加号 (未输入时显示)
            AnimatedVisibility(
                visible = !isTyping,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    IconButton(onClick = { /* 语音 */ } , modifier = Modifier.size(30.dp)) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_btn_speak_now),
                            contentDescription = "Voice",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { /* 加号 */ }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_input_add),
                            contentDescription = "Add",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // 右侧：发送按钮 (输入时显示)
            AnimatedVisibility(
                visible = isTyping,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                IconButton(
                    onClick = {
                        onSendMessage(inputText)
                        inputText = ""
                    },
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .background(Color(0xFF3D5CFF), CircleShape) // 豆包蓝圆形背景
                        .size(35.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_send),
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 *   测试
 */
@Preview(showBackground = true, name = "Local Chat Preview")
@Composable
fun PreviewLocalChat() {
    // 1. 准备一些假数据用于预览
    val mockMessages = listOf(
        Message(
            id = "1",
            role = MessageRole.ASSISTANT,
            content = "你好！我是运行在你手机上的本地大模型。我可以离线为你提供帮助，无需联网，速度超快！\n\n请问有什么我可以帮你的吗？",
            status = MessageStatus.SUCCESS
        ),
        Message(
            id = "2",
            role = MessageRole.USER,
            content = "请介绍一下你自己，并且写一段冒泡排序的代码。",
            status = MessageStatus.SUCCESS
        ),
        Message(
            id = "3",
            role = MessageRole.ASSISTANT,
            content = "当然可以。我是由 MLC-LLM 驱动的本地语言模型。\n\n这是冒泡排序的 Kotlin 实现：\n\n```kotlin\nfun bubbleSort(arr: IntArray) {\n    // ...\n}\n```",
            status = MessageStatus.SUCCESS
        )
    )

    MaterialTheme {
        // 2. 调用 UI 组件并传入假数据
        LocalChatScreen(
            modelName = "Doubao-Local-4bit", // 假的模型名字
            messages = mockMessages,         // 传入假消息列表
            onBackClick = {},                // 空的回调
            onSendMessage = {}               // 空的回调
        )
    }
}
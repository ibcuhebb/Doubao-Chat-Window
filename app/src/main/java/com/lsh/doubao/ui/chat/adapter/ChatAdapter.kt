package com.lsh.doubao.ui.chat.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lsh.doubao.R
import com.lsh.doubao.data.model.Message
import com.lsh.doubao.data.model.MessageRole

class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == MessageRole.USER) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_message_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_ai, parent, false)
            AiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserViewHolder) {
            holder.bind(message)
        } else if (holder is AiViewHolder) {
            holder.bind(message)
        }
    }

    // 用户消息 ViewHolder
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)

        fun bind(message: Message) {
            tvContent.text = message.content
        }
    }

    // AI 消息 ViewHolder (包含交互逻辑)
    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvReasoning: TextView = itemView.findViewById(R.id.tv_reasoning)
        private val ivCopy: ImageView = itemView.findViewById(R.id.iv_copy)
        private val ivLike: ImageView = itemView.findViewById(R.id.iv_like)
        private val ivCollect: ImageView = itemView.findViewById(R.id.iv_collect)

        fun bind(message: Message) {
            val cleanContent = message.content.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
            tvContent.text = cleanContent

            // 处理深度思考内容显示
            if (!message.reasoningContent.isNullOrEmpty()) {
                tvReasoning.visibility = View.VISIBLE
                tvReasoning.text = message.reasoningContent
            } else {
                tvReasoning.visibility = View.GONE
            }

            // --- 按钮点击逻辑 ---

            // 1. 复制功能
            ivCopy.setOnClickListener {
                copyToClipboard(itemView.context, message.content)
                animateIconChange(ivCopy, R.drawable.ic_success, R.drawable.ic_copy)
                Toast.makeText(itemView.context, "已复制", Toast.LENGTH_SHORT).show()
            }

            // 2. 点赞功能
            ivLike.setOnClickListener {
                animateIconChange(ivLike, R.drawable.ic_success, R.drawable.ic_thumb_up)
                Toast.makeText(itemView.context, "已点赞", Toast.LENGTH_SHORT).show()
            }

            // 3. 收藏功能
            ivCollect.setOnClickListener {
                animateIconChange(ivCollect, R.drawable.ic_success, R.drawable.ic_bookmark)
                Toast.makeText(itemView.context, "已加入收藏", Toast.LENGTH_SHORT).show()
            }
        }

        // 辅助方法：复制到剪切板
        private fun copyToClipboard(context: Context, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI Chat", text)
            clipboard.setPrimaryClip(clip)
        }

        // 辅助方法：图标变化动画 (变对号 -> 延迟 2秒 -> 变回原样)
        private fun animateIconChange(imageView: ImageView, successIconRes: Int, originalIconRes: Int) {
            // 切换到成功图标
            imageView.setImageResource(successIconRes)

            // 2秒后恢复
            imageView.postDelayed({
                imageView.setImageResource(originalIconRes)
            }, 2000)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
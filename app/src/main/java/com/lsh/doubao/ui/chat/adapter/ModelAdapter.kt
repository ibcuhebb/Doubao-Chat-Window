package com.lsh.doubao.ui.chat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lsh.doubao.R
import com.lsh.doubao.ui.chat.model.ModelUiBean

class ModelAdapter(
    private val onModelSelected: (ModelUiBean) -> Unit
) : ListAdapter<ModelUiBean, ModelAdapter.ModelViewHolder>(ModelDiffCallback()) {

    // 当前选中的模型ID，用于显示对勾
    var currentModelId: String = ""
        set(value) {
            field = value
            notifyDataSetChanged() // 刷新列表以更新对勾状态
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_select, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_model_name)
        private val ivCheck: ImageView = itemView.findViewById(R.id.iv_check)

        fun bind(model: ModelUiBean) {
            tvName.text = model.displayName

            // 选中状态控制
            val isSelected = model.id == currentModelId
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            // 字体加粗增强选中感
            tvName.typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

            itemView.setOnClickListener {
                onModelSelected(model)
            }
        }
    }

    class ModelDiffCallback : DiffUtil.ItemCallback<ModelUiBean>() {
        override fun areItemsTheSame(oldItem: ModelUiBean, newItem: ModelUiBean): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ModelUiBean, newItem: ModelUiBean): Boolean {
            return oldItem == newItem
        }
    }
}
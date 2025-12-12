package com.lsh.doubao.ui.chat.model

/**
 *  用于本地模型加载
 */
data class ModelUiBean(
    val id: String,
    val displayName: String,
    val isLocal: Boolean,
    val modelLib: String = ""
)
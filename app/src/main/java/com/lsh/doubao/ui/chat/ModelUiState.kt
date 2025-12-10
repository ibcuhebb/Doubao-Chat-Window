//package com.lsh.doubao.ui.chat
//
//import com.lsh.doubao.data.model.ModelRecord
//import com.lsh.doubao.ui.chat.model.ModelUiBean
//
///**
// * 用于 UI 展示的模型包装类
// * 统一了 "远端模型" 和 "本地模型"
// */
//sealed class ModelUiBean(val id: String, val displayName: String) {
//    // 1. 远端模型 (你的豆包 API)
//    data object RemoteDoubao : ModelUiBean("doubao-remote", "Doubao-Flash (Remote)")
//
//    // 2. 本地模型 (来自 MLC 配置文件)
//    data class Local(val record: ModelRecord) : ModelUiBean(record.modelId, record.modelId)
//}
//
///**
// * 下载状态回调
// */
//sealed class DownloadState {
//    data object Idle : DownloadState()
//    data class Progress(val percentage: Int) : DownloadState()
//    data object Finished : DownloadState()
//    data class Error(val msg: String) : DownloadState()
//}
package com.lsh.doubao.data.local.engine

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.lsh.doubao.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * 移植自 MLCChat 的 AppViewModel。
 * 负责管理本地模型的列表、配置读取以及下载任务。
 */
class LocalModelManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: LocalModelManager? = null

        fun getInstance(context: Context): LocalModelManager {
            return instance ?: synchronized(this) {
                instance ?: LocalModelManager(context.applicationContext).also { instance = it }
            }
        }

        const val AppConfigFilename = "mlc-app-config.json"
        const val ModelConfigFilename = "mlc-chat-config.json"
        const val ParamsConfigFilename = "tensor-cache.json"
        const val ModelUrlSuffix = "resolve/main/"
    }

    private val gson = Gson()
    private val appDirFile = context.getExternalFilesDir("")
    private val scope = CoroutineScope(Dispatchers.IO)

    // 对外暴露的模型列表状态 (包含下载进度等)
    private val _modelListState = MutableStateFlow<List<ModelState>>(emptyList())
    val modelListState: StateFlow<List<ModelState>> = _modelListState.asStateFlow()

    // 记录 AppConfig
    private var appConfig = AppConfig(mutableListOf(), mutableListOf())
    private val modelIdSet = mutableSetOf<String>()

    init {
        loadAppConfig()
    }

    private fun loadAppConfig() {
        val appConfigFile = File(appDirFile, AppConfigFilename)
        val jsonString: String = if (!appConfigFile.exists()) {
            try {
                context.assets.open(AppConfigFilename).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                // 如果 assets 里没有，就用空的
                "{ \"model_libs\": [], \"model_list\": [] }"
            }
        } else {
            appConfigFile.readText()
        }

        appConfig = gson.fromJson(jsonString, AppConfig::class.java)
        refreshModelList()
    }

    private fun refreshModelList() {
        modelIdSet.clear()
        val newList = mutableListOf<ModelState>()

        for (modelRecord in appConfig.modelList) {
            val modelDirFile = File(appDirFile, modelRecord.modelId)
            val modelConfigFile = File(modelDirFile, ModelConfigFilename)

            if (modelConfigFile.exists()) {
                val modelConfigString = modelConfigFile.readText()
                val modelConfig = gson.fromJson(modelConfigString, ModelConfig::class.java)
                // 覆盖关键信息，确保一致
                modelConfig.modelId = modelRecord.modelId
                modelConfig.modelLib = modelRecord.modelLib
                modelConfig.estimatedVramBytes = modelRecord.estimatedVramBytes
                addModelToStateList(newList, modelConfig, modelRecord.modelUrl)
            } else {
                // 配置文件不存在，先尝试下载配置文件
                downloadModelConfig(
                    if (modelRecord.modelUrl.endsWith("/")) modelRecord.modelUrl else "${modelRecord.modelUrl}/",
                    modelRecord
                )
            }
        }
        _modelListState.value = newList
    }

    private fun addModelToStateList(list: MutableList<ModelState>, modelConfig: ModelConfig, modelUrl: String) {
        modelIdSet.add(modelConfig.modelId)
        list.add(
            ModelState(
                modelConfig,
                modelUrl + if (modelUrl.endsWith("/")) "" else "/",
                File(appDirFile, modelConfig.modelId)
            )
        )
    }

    private fun downloadModelConfig(modelUrl: String, modelRecord: ModelRecord) {
        thread(start = true) {
            try {
                val url = URL("${modelUrl}${ModelUrlSuffix}${ModelConfigFilename}")
                val tempId = UUID.randomUUID().toString()
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val tempFile = File(downloadsDir, tempId)

                url.openStream().use { input ->
                    Channels.newChannel(input).use { src ->
                        FileOutputStream(tempFile).use { fos ->
                            fos.channel.transferFrom(src, 0, Long.MAX_VALUE)
                        }
                    }
                }

                if (tempFile.exists()) {
                    val modelConfigString = tempFile.readText()
                    val modelConfig = gson.fromJson(modelConfigString, ModelConfig::class.java)
                    modelConfig.modelId = modelRecord.modelId
                    modelConfig.modelLib = modelRecord.modelLib
                    modelConfig.estimatedVramBytes = modelRecord.estimatedVramBytes

                    val modelDirFile = File(appDirFile, modelConfig.modelId)
                    if (!modelDirFile.exists()) modelDirFile.mkdirs()

                    val modelConfigFile = File(modelDirFile, ModelConfigFilename)
                    tempFile.copyTo(modelConfigFile, overwrite = true)
                    tempFile.delete()

                    // 下载完 Config 后，刷新列表，UI 就会显示这个模型（状态为 Initializing -> Indexing）
                    scope.launch {
                        refreshModelList()
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalModelManager", "Download config failed: ${e.message}")
            }
        }
    }

    /**
     * 单个模型的状态管理类 (内部类移植)
     * 负责具体的下载逻辑
     */
    inner class ModelState(
        val modelConfig: ModelConfig,
        private val modelUrl: String,
        val modelDirFile: File
    ) {
        // 使用 Flow 让 UI 可以观察进度变化
        val initState = MutableStateFlow(ModelInitState.Initializing)
        val progress = MutableStateFlow(0)
        val total = MutableStateFlow(1)

        private var paramsConfig = ParamsConfig(emptyList())
        private val remainingTasks = ConcurrentHashMap.newKeySet<DownloadTask>()
        private val downloadingTasks = ConcurrentHashMap.newKeySet<DownloadTask>()
        private val maxDownloadTasks = 3

        init {
            switchToInitializing()
        }

        private fun switchToInitializing() {
            val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
            if (paramsConfigFile.exists()) {
                loadParamsConfig()
                switchToIndexing()
            } else {
                downloadParamsConfig()
            }
        }

        private fun loadParamsConfig() {
            val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
            val jsonString = paramsConfigFile.readText()
            paramsConfig = gson.fromJson(jsonString, ParamsConfig::class.java)
        }

        private fun downloadParamsConfig() {
            thread(start = true) {
                try {
                    val url = URL("${modelUrl}${ModelUrlSuffix}${ParamsConfigFilename}")
                    val tempId = UUID.randomUUID().toString()
                    val tempFile = File(modelDirFile, tempId)
                    url.openStream().use { input ->
                        Channels.newChannel(input).use { src ->
                            FileOutputStream(tempFile).use { fos ->
                                fos.channel.transferFrom(src, 0, Long.MAX_VALUE)
                            }
                        }
                    }
                    val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
                    tempFile.renameTo(paramsConfigFile)

                    scope.launch {
                        loadParamsConfig()
                        switchToIndexing()
                    }
                } catch (e: Exception) {
                    Log.e("ModelState", "Download params config failed", e)
                }
            }
        }

        // 检查本地文件完整性
        private fun switchToIndexing() {
            initState.value = ModelInitState.Indexing
            var curProgress = 0
            var curTotal = modelConfig.tokenizerFiles.size + paramsConfig.paramsRecords.size

            // 检查 Tokenizer
            for (filename in modelConfig.tokenizerFiles) {
                val file = File(modelDirFile, filename)
                if (file.exists()) {
                    curProgress++
                } else {
                    remainingTasks.add(DownloadTask(URL("${modelUrl}${ModelUrlSuffix}${filename}"), file))
                }
            }
            // 检查权重参数
            for (record in paramsConfig.paramsRecords) {
                val file = File(modelDirFile, record.dataPath)
                if (file.exists()) {
                    curProgress++
                } else {
                    remainingTasks.add(DownloadTask(URL("${modelUrl}${ModelUrlSuffix}${record.dataPath}"), file))
                }
            }

            progress.value = curProgress
            total.value = curTotal

            if (curProgress < curTotal) {
                switchToPaused()
            } else {
                switchToFinished()
            }
        }

        // 公用操作方法

        fun handleStart() {
            switchToDownloading()
        }

        fun handlePause() {
            initState.value = ModelInitState.Pausing
        }

        // 内部状态机

        private fun switchToPaused() {
            initState.value = ModelInitState.Paused
        }

        private fun switchToFinished() {
            initState.value = ModelInitState.Finished
        }

        private fun switchToDownloading() {
            initState.value = ModelInitState.Downloading
            // 启动初始的一批下载任务
            val iterator = remainingTasks.iterator()
            while (iterator.hasNext() && downloadingTasks.size < maxDownloadTasks) {
                val task = iterator.next()
                startDownloadTask(task)
            }
        }

        private fun startDownloadTask(task: DownloadTask) {
            if (downloadingTasks.contains(task)) return
            downloadingTasks.add(task)

            thread(start = true) {
                try {
                    val tempId = UUID.randomUUID().toString()
                    val tempFile = File(modelDirFile, tempId)
                    task.url.openStream().use { input ->
                        Channels.newChannel(input).use { src ->
                            FileOutputStream(tempFile).use { fos ->
                                fos.channel.transferFrom(src, 0, Long.MAX_VALUE)
                            }
                        }
                    }
                    tempFile.renameTo(task.file)

                    scope.launch {
                        handleFinishDownload(task)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 简单重试逻辑或错误处理略
                }
            }
        }

        private fun handleFinishDownload(task: DownloadTask) {
            remainingTasks.remove(task)
            downloadingTasks.remove(task)
            progress.value += 1

            if (initState.value == ModelInitState.Downloading) {
                if (remainingTasks.isEmpty() && downloadingTasks.isEmpty()) {
                    switchToFinished()
                } else {
                    // 继续下一个
                    for (nextTask in remainingTasks) {
                        if (!downloadingTasks.contains(nextTask)) {
                            startDownloadTask(nextTask)
                            break
                        }
                    }
                }
            } else if (initState.value == ModelInitState.Pausing && downloadingTasks.isEmpty()) {
                switchToPaused()
            }
        }

        fun isReady(): Boolean = initState.value == ModelInitState.Finished
    }
}
package com.lsh.doubao.data.remote.api

import com.lsh.doubao.data.model.ChatRequest
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 *  Retrofit 接口
 */
interface ChatApiService {

    // 使用 OpenAI 兼容路径
    // @Streaming 是必须的，防止 Retrofit 把整个响应读进内存，导致内存溢出或无法实时显示
    @Streaming
    @POST("api/v3/chat/completions")
    fun streamChat(@Body request: ChatRequest): Call<ResponseBody>
}
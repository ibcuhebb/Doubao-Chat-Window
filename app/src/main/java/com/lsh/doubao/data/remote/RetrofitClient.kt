package com.lsh.doubao.data.remote

import com.lsh.doubao.data.remote.api.ChatApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Base URL
    private const val BASE_URL = "https://ark.cn-beijing.volces.com/"

    // API Key
    private const val API_KEY = "385a992e-1184-4dd6-a30e-0bd967937f31"

    // 配置 OkHttp 客户端
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            // 1. 添加 API Key 拦截器
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $API_KEY")
                    .header("Content-Type", "application/json")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            // 2. 添加日志拦截器 (方便调试)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS // 流式请求建议只打 Header，否则 Body 会乱码
            })
            // 3. 超时设置 (大模型生成比较慢，这里我设长一点)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // 创建 Retrofit 实例
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 公开的 API 服务实例
    val apiService: ChatApiService by lazy {
        retrofit.create(ChatApiService::class.java)
    }
}
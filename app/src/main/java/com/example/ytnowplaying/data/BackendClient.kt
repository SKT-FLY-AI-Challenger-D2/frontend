package com.example.ytnowplaying.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "YtApi"

class BackendClient(baseUrl: String) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(BackendApi::class.java)

    suspend fun search(
        videoKey: String,
        title: String,
        channel: String,
        duration: Long?           // ✅ 초 단위
    ): String? {
        Log.d(TAG, "[REQ /search] key=$videoKey title='${title.take(60)}' channel='${channel.take(40)}' duration=$duration")

        val res = api.searchVideo(
            SearchRequestDto(
                title = title,
                channel = channel,
                duration = duration
            )
        )

        Log.d(
            TAG,
            "[RES /search] key=$videoKey found=${res.found} vid=${res.video_id ?: "null"} " +
                    "msg=${res.message ?: "null"} err=${res.error ?: "null"}"
        )

        val text = res.analysis_result.trim()
        return text.takeIf { it.isNotBlank() }
    }
}
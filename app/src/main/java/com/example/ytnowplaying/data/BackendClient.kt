package com.example.ytnowplaying.data

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG = "YtApi"

class BackendClient(baseUrl: String) {

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl) // "http://10.0.2.2:8000/" 형태
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(BackendApi::class.java)

    // 반환값: 표시기에 넘길 "경고/분석 문구" (없으면 null)
    suspend fun search(
        videoKey: String,
        title: String,
        channel: String
    ): String? {
        val ch = channel.trim()
        Log.d(TAG, "[REQ /search] key=$videoKey title='${title.take(60)}' channel='${ch.take(40)}'")

        val res = api.searchVideo(SearchRequestDto(title = title, channel = ch))

        Log.d(TAG, "[RES /search] key=$videoKey found=${res.found} vid=${res.video_id ?: "null"} msg=${res.message ?: "null"} err=${res.error ?: "null"}")

        val text = res.analysis_result.trim()
        return text.takeIf { it.isNotBlank() }
    }

}

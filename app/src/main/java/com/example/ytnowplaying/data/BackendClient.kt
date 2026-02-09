package com.example.ytnowplaying.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "YtApi"

class BackendClient(
    private val baseUrl: String,
    private val useFake: Boolean = false
) {

    // ✅ 10초 제한을 늘림 (원하면 숫자만 조절)
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)  // 연결(접속) 시도 최대 30초
        .readTimeout(300, TimeUnit.SECONDS)     // 서버 응답 읽기 최대 60초
        .writeTimeout(300, TimeUnit.SECONDS)    // 요청 바디 전송 최대 30초
        .callTimeout(600, TimeUnit.SECONDS)     // 전체 호출(연결+전송+응답) 최대 60초
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl) // "http://10.0.2.2:8000/" 형태
        .client(okHttp)   // ✅ 이 줄이 핵심
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(BackendApi::class.java)

    suspend fun search(
        videoKey: String,
        title: String,
        channel: String?
    ): String? {

        if (useFake) {
            // 네트워크 없이 테스트용 응답
            kotlinx.coroutines.delay(1200) // 응답 지연도 흉내 가능
            return "FAKE 분석 결과\n- title=${title.take(40)}\n- channel=${channel}\n- key=${videoKey.take(60)}"
        }
        val ch = channel?.trim().takeUnless { it.isNullOrEmpty() } ?: "(unknown)"
        Log.d(TAG, "[REQ /search] key=$videoKey title='${title.take(60)}' channel='${ch.take(40)}'")

        val res = api.searchVideo(SearchRequestDto(title = title, channel = ch))

        Log.d(
            TAG,
            "[RES /search] key=$videoKey found=${res.found} vid=${res.video_id ?: "null"} " +
                    "msg=${res.message ?: "null"} err=${res.error ?: "null"}"
        )

        val text = res.analysis_result.trim()
        return text.takeIf { it.isNotBlank() }
    }
}

package com.example.ytnowplaying.data

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "YtApi"

class BackendClient(baseUrl: String) {

    private val gson = Gson()

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

    /**
     * API 명세서 기준: 요청 body는 title/channel/duration ONLY (추가 필드 금지)
     */
    suspend fun analyze(
        title: String,
        channel: String,
        duration: Long?,
    ): AnalyzeResponse? {
        val t = title.trim()
        val ch = channel.trim()
        val dur = duration ?: 0L

        Log.d(
            TAG,
            "[REQ /api/videos/analysis] title='${t.take(60)}' channel='${ch.take(40)}' duration=$dur"
        )

        return try {
            val res = api.analyze(
                AnalyzeRequest(
                    title = t,
                    channel = ch,
                    duration = dur
                )
            )

            // ✅ 응답 요약 로그(핵심 필드)
            Log.d(
                TAG,
                "[RES /api/videos/analysis] " +
                        "video_id='${res.videoId}' " +
                        "score=${res.finalScore} " +
                        "risk=${res.finalRiskLevel} " +
                        "found=${res.found} " +
                        "short='${(res.shortReport ?: "").replace("\n", " ").take(160)}' " +
                        "msg='${(res.message ?: "").replace("\n", " ").take(120)}' " +
                        "err='${(res.error ?: "").replace("\n", " ").take(120)}' " +
                        "evidence=${res.dangerEvidence?.size ?: 0}"
            )

            // ✅ 원본에 가까운 로그(길이 제한) - 필요 없으면 이 줄 삭제
            Log.v(TAG, "[RES-RAW /api/videos/analysis] ${gson.toJson(res).take(2000)}")

            res
        } catch (e: Exception) {
            Log.e(TAG, "[API] analyze failed: ${e.message}", e)
            null
        }
    }
}

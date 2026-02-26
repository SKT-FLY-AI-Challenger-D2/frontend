package com.example.ytnowplaying.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Backend API (API 명세서 기준)
 *
 * POST /api/videos/analysis
 * Request body: { title, channel, duration }
 * Response: final_score, final_risk_level(0/1/2), danger_evidence, analysis_report, short_report ...
 */
interface BackendApi {

    @POST("api/videos/analysis")
    suspend fun analyze(@Body request: AnalyzeRequest): AnalyzeResponse
}

data class AnalyzeRequest(
    @SerializedName("title") val title: String,
    @SerializedName("channel") val channel: String,
    @SerializedName("duration") val duration: Long,
)

data class AnalyzeResponse(
    // ===== 기본 메타 =====
    @SerializedName("video_id") val videoId: String? = null,   // Int? -> String?
    @SerializedName("youtube_url") val youtubeUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("channel_title") val channelTitle: String? = null,
    @SerializedName("found") val found: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,

    // ===== 최신 필드 =====
    @SerializedName("final_score") val finalScore: Float?,     // Double? -> Float?
    @SerializedName("final_risk_level") val finalRiskLevel: Int?,
    @SerializedName("danger_evidence") val dangerEvidence: List<String>?,
    @SerializedName("short_report") val shortReport: String?,
    @SerializedName("analysis_report") val analysisReport: String?,
)

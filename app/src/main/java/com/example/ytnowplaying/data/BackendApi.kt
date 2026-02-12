package com.example.ytnowplaying.data

import retrofit2.http.Body
import retrofit2.http.POST

data class SearchRequestDto(
    val title: String,
    val channel: String,
    val duration: Long?          // ✅ 초 단위
)

data class SearchResponseDto(
    val video_id: String?,
    val youtube_url: String?,
    val title: String?,
    val channel_title: String?,
    val found: Boolean,
    val message: String?,
    val analysis_result: String,
    val error: String?
)

interface BackendApi {
    @POST("api/videos/analysis")
    suspend fun searchVideo(@Body request: SearchRequestDto): SearchResponseDto
}

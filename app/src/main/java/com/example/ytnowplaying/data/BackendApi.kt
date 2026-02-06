package com.example.ytnowplaying.data

import retrofit2.http.Body
import retrofit2.http.POST

data class SearchRequestDto(
    val title: String,
    val channel: String
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
    @POST("search")
    suspend fun searchVideo(@Body request: SearchRequestDto): SearchResponseDto
}

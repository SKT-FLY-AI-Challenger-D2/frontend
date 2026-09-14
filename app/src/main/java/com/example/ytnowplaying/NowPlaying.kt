package com.example.ytnowplaying

import android.media.MediaMetadata
import android.media.session.MediaController
import android.util.Log

const val YOUTUBE_PKG = "com.google.android.youtube"

private const val TAG_NP = "YTNowPlaying-MetaProbe"

data class NowPlayingInfo(
    val title: String,
    val channel: String? = null,
    val duration: Long? = null, // ✅ 초 단위
    val videoId: String? = null // 서버 재검색 없이 바로 식별하기 위한 후보 ID(정찰 단계)
)

object NowPlayingFetcher {

    // YouTube 썸네일 URL 패턴에서 videoId 추출: https://i.ytimg.com/vi/{videoId}/... 또는
    // https://.../vi_webp/{videoId}/... 등. YouTube가 media URI를 직접 videoId로 안 채워줘도
    // 아이콘/아트 URI에는 썸네일 URL이 들어올 가능성이 높아 이 경로도 같이 시도한다.
    private val THUMB_VIDEO_ID_REGEX = Regex("""/vi(?:_webp)?/([a-zA-Z0-9_-]{11})/""")

    private fun extractVideoIdFromUri(uri: android.net.Uri?): String? {
        if (uri == null) return null
        val s = uri.toString()
        THUMB_VIDEO_ID_REGEX.find(s)?.let { return it.groupValues[1] }
        return null
    }

    fun extractFromMediaController(controller: MediaController): NowPlayingInfo? {
        val md = controller.metadata ?: return null

        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isBlank()) return null

        val channelOrArtist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        // MediaMetadata duration은 ms 단위로 들어온다.
        // 앱 전반에서는 초 단위를 사용하므로 여기서 변환한다.
        val duration = runCatching { md.getLong(MediaMetadata.METADATA_KEY_DURATION) }
            .getOrNull()
            ?.takeIf { it > 0L }
            ?.let { it / 1000L }

        // --- 정찰(TASK 예정): videoId를 직접 얻을 수 있는지 후보 필드를 모두 로그로 남긴다.
        // 서버가 title/channel로 유튜브를 재검색하는 현재 방식은 검색 결과가 실시간으로
        // 바뀌면(인기 급상승 영상 등) 엉뚱한 영상이 매칭되는 문제가 있다. 여기서 videoId를
        // 안정적으로 얻을 수 있으면 재검색 자체를 없앨 수 있다.
        val mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val mediaUri = md.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
        val descMediaId = runCatching { controller.metadata?.description?.mediaId }.getOrNull()
        val descMediaUri = runCatching { controller.metadata?.description?.mediaUri }.getOrNull()
        val artUri = runCatching { md.getString(MediaMetadata.METADATA_KEY_ART_URI) }.getOrNull()
        val albumArtUri = runCatching { md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) }.getOrNull()
        val displayIconUri = runCatching { md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) }.getOrNull()

        Log.i(
            TAG_NP,
            "mediaId=$mediaId mediaUri=$mediaUri descMediaId=$descMediaId " +
                "descMediaUri=$descMediaUri artUri=$artUri albumArtUri=$albumArtUri " +
                "displayIconUri=$displayIconUri title='${title.take(40)}'"
        )

        val videoId = mediaId?.takeIf { it.length == 11 }
            ?: descMediaId?.takeIf { it.length == 11 }
            ?: extractVideoIdFromUri(mediaUri?.let { android.net.Uri.parse(it) })
            ?: extractVideoIdFromUri(descMediaUri)
            ?: extractVideoIdFromUri(artUri?.let { android.net.Uri.parse(it) })
            ?: extractVideoIdFromUri(albumArtUri?.let { android.net.Uri.parse(it) })
            ?: extractVideoIdFromUri(displayIconUri?.let { android.net.Uri.parse(it) })

        if (videoId != null) {
            Log.i(TAG_NP, "videoId 추출 성공: $videoId (title='${title.take(40)}')")
        } else {
            Log.w(TAG_NP, "videoId 추출 실패 — 위 필드들 중 쓸만한 게 없었음")
        }

        return NowPlayingInfo(
            title = title,
            channel = channelOrArtist,
            duration = duration,
            videoId = videoId
        )
    }
}

package com.example.ytnowplaying

import android.media.MediaMetadata
import android.media.session.MediaController

const val YOUTUBE_PKG = "com.google.android.youtube"

data class NowPlayingInfo(
    val title: String,
    val channel: String? = null,
    val duration: Long? = null // ✅ 초 단위
)

object NowPlayingFetcher {

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

        return NowPlayingInfo(
            title = title,
            channel = channelOrArtist,
            duration = duration
        )
    }
}

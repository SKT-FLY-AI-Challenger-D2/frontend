package com.example.ytnowplaying

import android.media.MediaMetadata
import android.media.session.MediaController

const val YOUTUBE_PKG = "com.google.android.youtube"

data class NowPlayingInfo(
    val title: String,
    val channel: String? = null,
    val durationMs: Long? = null
)

object NowPlayingFetcher {

    fun extractFromMediaController(controller: MediaController): NowPlayingInfo? {
        val md = controller.metadata ?: return null

        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isBlank()) return null

        val channelOrArtist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val durationMs = runCatching { md.getLong(MediaMetadata.METADATA_KEY_DURATION) }
            .getOrNull()
            ?.takeIf { it > 0L }

        return NowPlayingInfo(
            title = title,
            channel = channelOrArtist,
            durationMs = durationMs
        )
    }
}
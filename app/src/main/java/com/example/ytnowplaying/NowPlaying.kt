package com.example.ytnowplaying

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log

private const val TAG = "YTNowPlaying"
const val YOUTUBE_PKG = "com.google.android.youtube"

const val ACTION_NOW_PLAYING = "com.example.ytnowplaying.ACTION_NOW_PLAYING"
const val EXTRA_TITLE = "extra_title"
const val EXTRA_CHANNEL = "extra_channel"
const val EXTRA_SOURCE = "extra_source"
const val EXTRA_DURATION_MS = "extra_duration_ms"


data class NowPlayingInfo(
    val title: String,
    val channel: String? = null,
    val durationMs: Long? = null, // ✅ 추가
    val source: Source,
    val updatedAtElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    enum class Source { MEDIA_SESSION, NOTIFICATION }
}

/**
 * 서비스/액티비티 간 “최근 감지값” 공유용 (저장/전송 없음, 앱 프로세스 메모리만 사용)
 */
object LastKnownNowPlaying {
    @Volatile
    var info: NowPlayingInfo? = null
}

object NowPlayingFetcher {

    fun fetchFromMediaSessions(context: Context): NowPlayingInfo? {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val listenerComponent = ComponentName(context, YoutubeNowPlayingListenerService::class.java)

        val controllers: List<MediaController> = try {
            msm.getActiveSessions(listenerComponent)
        } catch (se: SecurityException) {
            Log.w(TAG, "No notification-listener access yet. ${se.message}")
            return null
        } catch (t: Throwable) {
            Log.w(TAG, "getActiveSessions failed. ${t.message}")
            return null
        }

        val youtubeControllers = controllers.filter { it.packageName == YOUTUBE_PKG }
        if (youtubeControllers.isEmpty()) return null

        // 1) "재생 중" 우선
        val playing = youtubeControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val picked = playing ?: youtubeControllers.first()

        return extractFromMediaController(picked)
    }

    fun extractFromMediaController(controller: MediaController): NowPlayingInfo? {
        val md = controller.metadata ?: return null

        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isBlank()) return null

        // 유튜브에서 채널명이 artist로 들어오는 경우가 흔함(기기/버전별 상이)
        val channelOrArtist =
            md.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
                ?.takeIf { it.isNotBlank() }

        // ✅ duration 추출 (ms). 유튜브가 0/미제공일 수 있으므로 >0만 채택
        val durationMs = runCatching { md.getLong(MediaMetadata.METADATA_KEY_DURATION) }
            .getOrNull()
            ?.takeIf { it > 0L }

        return NowPlayingInfo(
            title = title,
            channel = channelOrArtist,
            durationMs = durationMs,
            source = NowPlayingInfo.Source.MEDIA_SESSION
        )
    }

    /**
     * 유튜브 알림에서 제목/채널 텍스트 파싱 (유튜브가 알림을 띄우는 경우에만)
     * 보통 알림 extras로 "총 길이(duration)"가 안정적으로 들어오지 않아 duration은 null 유지.
     */
    fun extractFromYoutubeNotification(sbn: StatusBarNotification): NowPlayingInfo? {
        val n: Notification = sbn.notification ?: return null
        val e = n.extras ?: return null

        val titleCs =
            e.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: e.getCharSequence(Notification.EXTRA_TITLE)

        val textCs =
            e.getCharSequence(Notification.EXTRA_TEXT)
                ?: e.getCharSequence(Notification.EXTRA_SUB_TEXT)

        var title = titleCs?.toString()?.trim().orEmpty()
        var channel = textCs?.toString()?.trim()

        if (title.isBlank()) return null

        // 흔한 케이스: title이 "YouTube" 같은 앱명으로 오고, 실제 영상 제목이 text로 오는 경우
        if (title.equals("YouTube", ignoreCase = true) && !channel.isNullOrBlank()) {
            title = channel
            channel = null
        }

        return NowPlayingInfo(
            title = title,
            channel = channel?.takeIf { it.isNotBlank() },
            durationMs = null, // ✅ 알림 폴백에서는 길이 미제공인 경우가 많아 null
            source = NowPlayingInfo.Source.NOTIFICATION
        )
    }
}

package com.example.ytnowplaying

import android.content.Intent
import android.media.session.MediaController
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

private const val TAG = "YTNowPlaying"


class YoutubeNowPlayingListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListener connected")

        // 연결 시점에 한 번 “현재 재생” 시도 (MediaSession 1순위)
        publish(NowPlayingFetcher.fetchFromMediaSessions(this))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != YOUTUBE_PKG) return

        // 1) MediaSession에서 먼저 갱신 시도 (더 “정상적인” now playing)
        val fromSession = NowPlayingFetcher.fetchFromMediaSessions(this)
        if (fromSession != null) {
            publish(fromSession)
            return
        }

        // 2) 폴백: 유튜브 알림 파싱
        val fromNoti = NowPlayingFetcher.extractFromYoutubeNotification(sbn)
        publish(fromNoti)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != YOUTUBE_PKG) return

        // 유튜브 알림이 내려간 경우 “감지 안 됨” 처리 (단, 백그라운드 재생이면 다시 올라올 수 있음)
        publish(null)
    }

    private fun publish(info: NowPlayingInfo?) {
        LastKnownNowPlaying.info = info

        val i = Intent(ACTION_NOW_PLAYING).setPackage(packageName)
        if (info != null) {
            i.putExtra(EXTRA_TITLE, info.title)
            i.putExtra(EXTRA_CHANNEL, info.channel)
            i.putExtra(EXTRA_SOURCE, info.source.name)
            if (info.durationMs != null) {
                i.putExtra(EXTRA_DURATION_MS, info.durationMs)
            }
        }
        sendBroadcast(i)

        Log.d(TAG, "publish: $info")
    }

}

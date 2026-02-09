package com.example.ytnowplaying

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.ytnowplaying.nowplaying.NowPlayingCache

private const val TAG = "YTNowPlaying"

class YoutubeNowPlayingListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var msm: MediaSessionManager? = null
    private var currentController: MediaController? = null

    // --- "영상 변경 확정"용 디바운스 ---
    private var pendingStableKey: String? = null
    private var pendingInfo: NowPlayingInfo? = null
    private var pendingRunnable: Runnable? = null

    private val DEBOUNCE_MS = 800L

    // --- channel 올 때까지 전송/캐시 보류(hold) ---
    private var holdStableKey: String? = null
    private var holdAttempts: Int = 0
    private var holdRunnable: Runnable? = null

    private val HOLD_RETRY_MS = 200L
    private val HOLD_MAX_TRIES = 15 // 3초

    // --- “캐시 업데이트” dedup (너무 자주 갱신 방지) ---
    private var lastCachedKey: String? = null
    private var lastCachedAtMs: Long = 0L
    private val CACHE_DEDUP_TTL_MS = 10_000L // 10초 내 동일 stableKey는 갱신 스킵(원하면 조절)

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            val info = currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
            if (info == null) {
                Log.d(TAG, "[MediaSession] metadata changed but info=null")
                return
            }
            scheduleConfirm(info)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // 필요 시 PLAYING 조건 등 추가 가능
        }
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachToYoutubeController(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListener connected")

        msm = getSystemService(MediaSessionManager::class.java)
        val cn = ComponentName(this, YoutubeNowPlayingListenerService::class.java)

        try {
            msm?.addOnActiveSessionsChangedListener(activeSessionsListener, cn, mainHandler)
        } catch (t: Throwable) {
            Log.w(TAG, "addOnActiveSessionsChangedListener failed: ${t.message}")
        }

        val controllers = try {
            msm?.getActiveSessions(cn).orEmpty()
        } catch (se: SecurityException) {
            Log.w(TAG, "No notification-listener access yet: ${se.message}")
            emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "getActiveSessions failed: ${t.message}")
            emptyList()
        }

        attachToYoutubeController(controllers)

        currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
            ?.let { scheduleConfirm(it) }
    }

    override fun onListenerDisconnected() {
        detachController()
        try { msm?.removeOnActiveSessionsChangedListener(activeSessionsListener) } catch (_: Throwable) {}
        msm = null
        Log.i(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        holdRunnable = null
        pendingStableKey = null
        holdStableKey = null
        super.onDestroy()
    }

    private fun attachToYoutubeController(controllers: List<MediaController>) {
        val youtube = controllers.filter { it.packageName == YOUTUBE_PKG }
        if (youtube.isEmpty()) {
            detachController()
            return
        }

        val picked = youtube.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: youtube.first()

        if (picked.sessionToken == currentController?.sessionToken) return

        detachController()
        currentController = picked

        try {
            picked.registerCallback(controllerCallback, mainHandler)
        } catch (t: Throwable) {
            Log.w(TAG, "registerCallback failed: ${t.message}")
        }

        Log.d(TAG, "Attached to YouTube controller")
    }

    private fun detachController() {
        currentController?.let {
            try { it.unregisterCallback(controllerCallback) } catch (_: Throwable) {}
        }
        currentController = null
    }

    private fun scheduleConfirm(info: NowPlayingInfo) {
        val stableKey = buildStableKey(info)

        if (pendingStableKey == stableKey) {
            pendingInfo = info
            return
        }

        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingStableKey = stableKey
        pendingInfo = info

        val r = Runnable {
            val confirmed = pendingInfo ?: return@Runnable
            pendingRunnable = null
            pendingStableKey = null
            pendingInfo = null
            onVideoConfirmed(confirmed)
        }
        pendingRunnable = r
        mainHandler.postDelayed(r, DEBOUNCE_MS)

        Log.d(TAG, "[pending] stableKey=$stableKey title='${info.title}'")
    }

    /**
     * 여기서는 "전송" 절대 하지 않는다.
     * channel이 채워진 info만 NowPlayingCache에 저장한다.
     */
    private fun onVideoConfirmed(info: NowPlayingInfo) {
        val stableKey = buildStableKey(info)
        val ch = info.channel?.trim().orEmpty()

        Log.i(TAG, "[CONFIRMED] title='${info.title}', channel='$ch', durationMs=${info.durationMs ?: -1}")

        if (ch.isBlank()) {
            Log.i(TAG, "[HOLD] channel empty -> wait. stableKey=$stableKey")
            startHoldForChannel(stableKey)
            return
        }

        // ✅ cache dedup은 channel 확인 이후에만 갱신 (HOLD->dedup 꼬임 방지)
        val now = android.os.SystemClock.elapsedRealtime()
        if (stableKey == lastCachedKey && (now - lastCachedAtMs) < CACHE_DEDUP_TTL_MS) {
            Log.d(TAG, "[cache-dedup] skip stableKey=$stableKey")
            return
        }
        lastCachedKey = stableKey
        lastCachedAtMs = now

        NowPlayingCache.update(stableKey, info)
        Log.i(TAG, "[CACHED] stableKey=$stableKey title='${info.title.take(60)}' channel='${ch.take(40)}'")
    }

    private fun startHoldForChannel(stableKey: String) {
        if (holdStableKey == stableKey && holdRunnable != null) return

        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdStableKey = stableKey
        holdAttempts = 0

        val r = object : Runnable {
            override fun run() {
                if (holdStableKey != stableKey) return
                holdAttempts++

                val latest = currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
                if (latest != null) {
                    val k2 = buildStableKey(latest)
                    val ch2 = latest.channel?.trim().orEmpty()

                    if (k2 == stableKey && ch2.isNotBlank()) {
                        Log.i(TAG, "[HOLD] channel arrived after retry=$holdAttempts -> cache now")
                        holdStableKey = null
                        holdRunnable = null
                        onVideoConfirmed(latest) // 이제 ch2가 있으므로 캐시에 저장됨
                        return
                    }
                }

                if (holdAttempts >= HOLD_MAX_TRIES) {
                    Log.w(TAG, "[HOLD] still empty after $HOLD_MAX_TRIES tries -> give up (no cache)")
                    holdStableKey = null
                    holdRunnable = null
                    return
                }

                mainHandler.postDelayed(this, HOLD_RETRY_MS)
            }
        }

        holdRunnable = r
        mainHandler.postDelayed(r, HOLD_RETRY_MS)
    }

    /**
     * stableKey는 channel이 늦게 와도 변하지 않게 해야 한다.
     * 그래서 title + duration만 사용한다.
     */
    private fun buildStableKey(info: NowPlayingInfo): String {
        val t = normalize(info.title)
        val d = info.durationMs?.let { (it / 1000L) } ?: -1L
        return "$t|$d"
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("\\s+"), " ").trim()
}

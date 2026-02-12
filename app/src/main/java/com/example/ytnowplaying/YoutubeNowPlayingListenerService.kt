package com.example.ytnowplaying

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.ytnowplaying.data.BackendClient
import com.example.ytnowplaying.nowplaying.NowPlayingCache
import com.example.ytnowplaying.overlay.OverlayController
import com.example.ytnowplaying.prefs.ModePrefs
import com.example.ytnowplaying.render.OverlayAlertRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // --- channel 올 때까지 보류(hold) ---
    private var holdStableKey: String? = null
    private var holdAttempts: Int = 0
    private var holdRunnable: Runnable? = null
    private val HOLD_RETRY_MS = 200L
    private val HOLD_MAX_TRIES = 15 // 3초

    // --- “캐시 업데이트” dedup ---
    private var lastCachedKey: String? = null
    private var lastCachedAtMs: Long = 0L
    private val CACHE_DEDUP_TTL_MS = 10_000L // 10초

    // --- “자동 전송” dedup ---
    private var lastSentKey: String? = null
    private var lastSentAtMs: Long = 0L
    private val SEND_DEDUP_TTL_MS = 10 * 60_000L // 10분

    @Volatile private var latestSendingKey: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = BackendClient("http://10.0.2.2:8000/")

    private val renderer by lazy {
        OverlayAlertRenderer(
            appCtx = applicationContext,
            autoDismissMs = 8_000L
        )
    }

    // ===== 버튼 표시/숨김 상태 머신(핵심) =====
    private var isButtonShown = false
    private var stopButtonRunnable: Runnable? = null
    private val STOP_BUTTON_GRACE_MS = 1_000L  // 3~5초 추천

    private fun updateFloatingButton(youtubeActive: Boolean) {
        // 백그라운드 모드면 버튼은 항상 꺼짐
        if (ModePrefs.isBackgroundModeEnabled(applicationContext)) {
            if (isButtonShown) {
                Log.d(TAG, "[BTN] bgMode=ON -> stop button")
                OverlayController.stop(applicationContext)
                isButtonShown = false
            }
            stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
            stopButtonRunnable = null
            return
        }

        // 오버레이 권한 없으면 버튼 표시 불가
        if (!Settings.canDrawOverlays(applicationContext)) {
            if (isButtonShown) {
                Log.d(TAG, "[BTN] no overlay permission -> stop button")
                OverlayController.stop(applicationContext)
                isButtonShown = false
            }
            stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
            stopButtonRunnable = null
            return
        }

        if (youtubeActive) {
            // stop 예약 취소
            stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
            stopButtonRunnable = null

            if (!isButtonShown) {
                Log.d(TAG, "[BTN] youtubeActive=true -> start button")
                OverlayController.start(applicationContext) // ★ 반드시 idempotent여야 함
                isButtonShown = true
            }
        } else {
            // 유튜브가 잠깐 사라졌다가 다시 잡히는 구간이 많아서 즉시 stop 금지
            if (!isButtonShown) return
            if (stopButtonRunnable != null) return

            val r = Runnable {
                Log.d(TAG, "[BTN] youtubeActive=false (grace passed) -> stop button")
                OverlayController.stop(applicationContext)
                isButtonShown = false
                stopButtonRunnable = null
            }
            stopButtonRunnable = r
            mainHandler.postDelayed(r, STOP_BUTTON_GRACE_MS)
        }
    }
    // =====================================

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
            // 선택: "유튜브가 실제로 재생 중일 때만 버튼" 원하면 아래로 바꿔도 됨.
            // val active = (state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING)
            // updateFloatingButton(active)
        }
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            val list = controllers.orEmpty()
            val yt = list.any { it.packageName == YOUTUBE_PKG }
            Log.d(TAG, "[YT-SESSION] active=$yt t=${android.os.SystemClock.elapsedRealtime()}")
            updateFloatingButton(yt)
            attachToYoutubeController(list)
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

        val yt = controllers.any { it.packageName == YOUTUBE_PKG }
        Log.d(TAG, "[YT-SESSION] active=$yt t=${android.os.SystemClock.elapsedRealtime()}")
        updateFloatingButton(yt)

        attachToYoutubeController(controllers)

        currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
            ?.let { scheduleConfirm(it) }
    }

    override fun onListenerDisconnected() {
        detachController()
        try { msm?.removeOnActiveSessionsChangedListener(activeSessionsListener) } catch (_: Throwable) {}
        msm = null

        // 버튼 정리
        stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
        stopButtonRunnable = null
        if (isButtonShown) {
            OverlayController.stop(applicationContext)
            isButtonShown = false
        }

        cancelHold()
        Log.i(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        pendingStableKey = null
        pendingInfo = null

        stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
        stopButtonRunnable = null

        cancelHold()
        scope.cancel()
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

        if (holdStableKey != null && holdStableKey != stableKey) cancelHold()

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

    private fun onVideoConfirmed(info: NowPlayingInfo) {
        val stableKey = buildStableKey(info)
        val ch = info.channel?.trim().orEmpty()

        Log.i(TAG, "[CONFIRMED] title='${info.title}', channel='$ch', duration=${info.duration ?: -1}")

        if (ch.isBlank()) {
            Log.i(TAG, "[HOLD] channel empty -> wait. stableKey=$stableKey")
            startHoldForChannel(stableKey)
            return
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (stableKey == lastCachedKey && (now - lastCachedAtMs) < CACHE_DEDUP_TTL_MS) {
            Log.d(TAG, "[cache-dedup] skip stableKey=$stableKey")
        } else {
            lastCachedKey = stableKey
            lastCachedAtMs = now
            NowPlayingCache.update(stableKey, info)
            Log.i(TAG, "[CACHED] stableKey=$stableKey title='${info.title.take(60)}' channel='${ch.take(40)}'")
        }

        if (!ModePrefs.isBackgroundModeEnabled(applicationContext)) return

        val now2 = android.os.SystemClock.elapsedRealtime()
        if (stableKey == lastSentKey && (now2 - lastSentAtMs) < SEND_DEDUP_TTL_MS) {
            Log.d(TAG, "[send-dedup] skip stableKey=$stableKey")
            return
        }
        lastSentKey = stableKey
        lastSentAtMs = now2
        latestSendingKey = stableKey

        scope.launch {
            val resultText: String? = try {
                backend.search(
                    videoKey = stableKey,
                    title = info.title,
                    channel = ch,
                    duration = info.duration
                )
            } catch (e: Exception) {
                Log.e(TAG, "[Backend] search failed", e)
                null
            }

            val finalText = resultText?.trim().orEmpty()

            withContext(Dispatchers.Main) {
                if (latestSendingKey != stableKey) return@withContext
                if (!ModePrefs.isBackgroundModeEnabled(applicationContext)) return@withContext

                if (finalText.isBlank()) renderer.clearWarning()
                else renderer.showWarning("! 영상에 문제가 있습니다") {
                    openReportFromOverlay(reportId = "demo", alertText = finalText)
                }
            }
        }
    }

    private fun openReportFromOverlay(reportId: String, alertText: String?) {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_OPEN_REPORT_ID, reportId)
            putExtra(MainActivity.EXTRA_FROM_OVERLAY, true)
            putExtra(MainActivity.EXTRA_ALERT_TEXT, alertText ?: "! 영상에 문제가 있습니다")
        }
        startActivity(i)
    }

    private fun startHoldForChannel(stableKey: String) {
        if (holdStableKey == stableKey && holdRunnable != null) return

        cancelHold()
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
                        Log.i(TAG, "[HOLD] channel arrived after retry=$holdAttempts -> proceed")
                        cancelHold()
                        onVideoConfirmed(latest)
                        return
                    }

                    if (k2 != stableKey) {
                        Log.i(TAG, "[HOLD] video changed while holding -> cancel hold. old=$stableKey new=$k2")
                        cancelHold()
                        return
                    }
                }

                if (holdAttempts >= HOLD_MAX_TRIES) {
                    Log.w(TAG, "[HOLD] still empty after $HOLD_MAX_TRIES tries -> give up")
                    cancelHold()
                    return
                }

                mainHandler.postDelayed(this, HOLD_RETRY_MS)
            }
        }

        holdRunnable = r
        mainHandler.postDelayed(r, HOLD_RETRY_MS)
    }

    private fun cancelHold() {
        holdRunnable?.let { mainHandler.removeCallbacks(it) }
        holdRunnable = null
        holdStableKey = null
        holdAttempts = 0
    }

    private fun buildStableKey(info: NowPlayingInfo): String {
        val t = normalize(info.title)
        val d = info.duration ?: -1L
        return "$t|$d"
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("\\s+"), " ").trim()
}

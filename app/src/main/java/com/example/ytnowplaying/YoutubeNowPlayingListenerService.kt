package com.example.ytnowplaying

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.ytnowplaying.data.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.ytnowplaying.render.OverlayAlertRenderer
import com.example.ytnowplaying.render.AlertRenderer
import kotlinx.coroutines.withContext



private const val TAG = "YTNowPlaying"

class YoutubeNowPlayingListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var msm: MediaSessionManager? = null
    private var currentController: MediaController? = null

    // --- "영상 변경 확정"용 최소 로직 ---
    private var pendingKey: String? = null
    private var pendingInfo: NowPlayingInfo? = null
    private var pendingRunnable: Runnable? = null

    // ✅ 전송 dedup (실제 전송 성공/시도 시점에만 갱신해야 함)
    private var lastSentKey: String? = null
    private var lastSentAtMs: Long = 0L

    private val DEBOUNCE_MS = 800L
    private val DEDUP_TTL_MS = 10 * 60_000L

    // --- channel 올 때까지 전송 보류(hold) ---
    private var holdKey: String? = null
    private var holdAttempts: Int = 0
    private var holdRunnable: Runnable? = null

    private val HOLD_RETRY_MS = 200L
    private val HOLD_MAX_TRIES = 15 // 3초

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 에뮬레이터 기준. 실기기는 PC LAN IP로 바꿔야 함.
    private val backend = BackendClient("http://10.0.2.2:8000/", useFake = true)

    private val renderer: AlertRenderer by lazy { OverlayAlertRenderer(applicationContext) }


    @Volatile private var latestVideoKey: String? = null

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
            // 필요하면 PLAYING에서만 처리하도록 제한 가능
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
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

        // 연결 직후 메타데이터가 있으면 확정 시도
        currentController
            ?.let { NowPlayingFetcher.extractFromMediaController(it) }
            ?.let { scheduleConfirm(it) }
    }

    override fun onListenerDisconnected() {
        detachController()
        try { msm?.removeOnActiveSessionsChangedListener(activeSessionsListener) } catch (_: Throwable) {}
        msm = null
        cancelHold()
        Log.i(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cancelHold()
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        pendingKey = null
        pendingInfo = null

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

    // --- 영상 변경 확정 로직(디바운스 + 중복억제) ---
    private fun scheduleConfirm(info: NowPlayingInfo) {
        val key = buildVideoKey(info)

        // ✅ 다른 영상으로 바뀌면 기존 HOLD는 의미 없으니 취소
        if (holdKey != null && holdKey != key) cancelHold()

        if (pendingKey == key) {
            pendingInfo = info
            return
        }

        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingKey = key
        pendingInfo = info

        val r = Runnable {
            val confirmed = pendingInfo ?: return@Runnable
            pendingRunnable = null
            pendingKey = null
            pendingInfo = null
            onVideoConfirmed(confirmed)
        }
        pendingRunnable = r
        mainHandler.postDelayed(r, DEBOUNCE_MS)

        Log.d(TAG, "[pending] key=$key title='${info.title}'")
    }

    private fun onVideoConfirmed(info: NowPlayingInfo) {
        // ✅ 전송/홀드 판단은 이 함수가 아니라 trySendOrHold에서만 수행
        trySendOrHold(info)
    }

    /**
     * ✅ 핵심:
     * - channel이 비어있으면 HOLD만 걸고 종료 (dedup 갱신 금지)
     * - channel이 있을 때만 dedup 체크 + lastSent 갱신 + 전송
     */
    private fun trySendOrHold(info: NowPlayingInfo) {
        val key = buildVideoKey(info)
        val ch = info.channel?.trim().orEmpty()

        if (ch.isBlank()) {
            Log.i(TAG, "[HOLD] channel empty -> do NOT send yet. key=$key")
            startHoldForChannel(key)
            return
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (key == lastSentKey && (now - lastSentAtMs) < DEDUP_TTL_MS) {
            Log.d(TAG, "[dedup] skip key=$key")
            return
        }

        // ✅ 여기서만 dedup state 갱신
        lastSentKey = key
        lastSentAtMs = now

        Log.i(TAG, "[CONFIRMED] title='${info.title}', channel='$ch', durationMs=${info.durationMs ?: -1}")

        latestVideoKey = key

        scope.launch {
            val alertText = try {
                backend.search(
                    videoKey = key,
                    title = info.title,
                    channel = ch
                )
            } catch (e: Exception) {
                Log.w(TAG, "[Backend] call failed: ${e.message}")
                null
            }

            Log.d(TAG, "[Backend] key=$key alertText=${alertText?.take(80) ?: "null"}")
            //

            withContext(Dispatchers.Main) {
                if (latestVideoKey != key) return@withContext // 늦게 온 응답 폐기

                if (alertText.isNullOrBlank()) {
                    renderer.clearWarning()
                } else {
                    renderer.showWarning(alertText)
                }
            }


        }
    }

    private fun startHoldForChannel(key: String) {
        if (holdKey == key && holdRunnable != null) return

        cancelHold()
        holdKey = key
        holdAttempts = 0

        val r = object : Runnable {
            override fun run() {
                if (holdKey != key) return

                holdAttempts++

                val latest = currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
                if (latest != null) {
                    val k2 = buildVideoKey(latest)
                    val ch2 = latest.channel?.trim().orEmpty()

                    // ✅ 같은 영상이고 channel이 채워졌으면: "전송 함수"로 바로 진입
                    if (k2 == key && ch2.isNotBlank()) {
                        Log.i(TAG, "[HOLD] channel arrived after retry=$holdAttempts -> send now")
                        cancelHold()
                        trySendOrHold(latest)
                        return
                    }

                    // ✅ 영상이 이미 바뀐 경우: HOLD는 의미 없으니 종료
                    if (k2 != key) {
                        Log.i(TAG, "[HOLD] video changed while holding -> cancel hold. old=$key new=$k2")
                        cancelHold()
                        return
                    }
                }

                if (holdAttempts >= HOLD_MAX_TRIES) {
                    Log.w(TAG, "[HOLD] channel still empty after $HOLD_MAX_TRIES tries -> give up (no send)")
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
        holdKey = null
        holdAttempts = 0
    }

    /**
     * ✅ key는 channel 포함하면 HOLD 중에 key가 바뀌어서 매칭이 깨질 수 있음.
     * 따라서 "title+duration" 같이 channel과 무관한 값으로 고정.
     */
    private fun buildVideoKey(info: NowPlayingInfo): String {
        val t = normalize(info.title)
        val d = info.durationMs?.let { (it / 1000L) } ?: -1L
        return "$t|$d"
    }

    private fun normalize(s: String): String {
        return s.lowercase().replace(Regex("\\s+"), " ").trim()
    }
}

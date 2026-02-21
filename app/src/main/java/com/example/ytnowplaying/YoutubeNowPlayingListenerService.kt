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
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.Severity
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
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "YTNowPlaying"

class YoutubeNowPlayingListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var msm: MediaSessionManager? = null
    private var currentController: MediaController? = null

    private var pendingStableKey: String? = null
    private var pendingInfo: NowPlayingInfo? = null
    private var pendingRunnable: Runnable? = null
    private val DEBOUNCE_MS = 800L

    private var holdStableKey: String? = null
    private var holdAttempts: Int = 0
    private var holdRunnable: Runnable? = null
    private val HOLD_RETRY_MS = 200L
    private val HOLD_MAX_TRIES = 15 // 3초

    private var lastCachedKey: String? = null
    private var lastCachedAtMs: Long = 0L
    private val CACHE_DEDUP_TTL_MS = 10_000L // 10초

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

    private var isButtonShown = false
    private var stopButtonRunnable: Runnable? = null
    private val STOP_BUTTON_GRACE_MS = 1_000L  // ✅ 1초 후 꺼짐

    private fun updateFloatingButton(youtubeActive: Boolean) {
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
            stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
            stopButtonRunnable = null

            if (!isButtonShown) {
                Log.d(TAG, "[BTN] youtubeActive=true -> start button")
                OverlayController.start(applicationContext) // idempotent 전제
                isButtonShown = true
            }
        } else {
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

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            val info = currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
            if (info == null) {
                Log.d(TAG, "[MediaSession] metadata changed but info=null")
                return
            }
            scheduleConfirm(info)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {}
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
            val apiRes = try {
                backend.analyze(
                    title = info.title,
                    channel = ch,
                    duration = info.duration
                )
            } catch (e: Exception) {
                Log.e(TAG, "[Backend] analyze failed", e)
                null
            }

            if (apiRes == null) {
                withContext(Dispatchers.Main) {
                    if (latestSendingKey != stableKey) return@withContext
                    if (!ModePrefs.isBackgroundModeEnabled(applicationContext)) return@withContext
                    // ✅ 통신 오류 오버레이 표시
                    renderer.showCommError()
                }
                return@launch
            }

            val severity = when (apiRes.finalRiskLevel ?: 1) {
                9 -> Severity.NOT_AD
                2 -> Severity.DANGER
                1 -> Severity.CAUTION
                0 -> Severity.SAFE
                else -> Severity.CAUTION
            }

            val scorePercent = ((apiRes.finalScore ?: 0f) * 100f)
                .roundToInt()
                .coerceIn(0, 100)

            val summaryRaw = apiRes.shortReport?.trim().orEmpty()
            val summary = if (severity == Severity.NOT_AD ) {
                "이 영상은 광고성 콘텐츠가 아닌 일반 정보 전달 영상으로 판단됩니다."
            } else {
                summaryRaw
            }

            val detail = apiRes.analysisReport?.trim().orEmpty()
                .ifBlank { summary }

            val dangerEvidence =
                if (severity == Severity.SAFE || severity == Severity.NOT_AD) emptyList()
                else apiRes.dangerEvidence.orEmpty().map { it.trim() }.filter { it.isNotBlank() }

            val reportId = UUID.randomUUID().toString()
            val report = Report(
                id = reportId,
                detectedAtEpochMs = System.currentTimeMillis(),
                title = info.title,
                channel = ch,
                durationSec = info.duration,
                scorePercent = scorePercent,
                severity = severity,
                dangerEvidence = dangerEvidence,
                summary = summary,
                detail = detail
            )

            withContext(Dispatchers.Main) {
                if (latestSendingKey != stableKey) return@withContext
                if (!ModePrefs.isBackgroundModeEnabled(applicationContext)) return@withContext

                Log.d(
                    TAG,
                    "[SAVE] stableKey=$stableKey reportId=$reportId severity=$severity score=$scorePercent summary='${summary.take(80)}'"
                )

                AppContainer.reportRepository.saveReport(report)

                Log.d(TAG, "[SAVE-DONE] reportId=$reportId stableKey=$stableKey")

                when (severity) {
                    Severity.DANGER -> {
                        renderer.showModal(
                            tone = OverlayAlertRenderer.Tone.DANGER,
                            title = "! 영상에 문제가 있습니다",
                            bodyLead = summary,
                            autoDismissOverrideMs = 0L
                        ) {
                            openReportFromOverlay(reportId = reportId, alertText = summary)
                        }
                    }

                    Severity.CAUTION -> {
                        renderer.showBanner(
                            tone = OverlayAlertRenderer.Tone.CAUTION,
                            title = "주의",
                            subtitle = "탭하여 보고서 보기",
                            autoDismissMs = 5_000L
                        ) {
                            openReportFromOverlay(reportId = reportId, alertText = summary)
                        }
                    }
                    Severity.SAFE,
                    Severity.NOT_AD -> {
                        renderer.clearAll()
                    }
                }

            }
        }
    }

    private fun openReportFromOverlay(reportId: String, alertText: String?) {
        Log.d(TAG, "[OPEN] reportId=$reportId fromOverlay=true alertLen=${(alertText ?: "").length}")
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_OPEN_REPORT_ID, reportId)
            putExtra(MainActivity.EXTRA_FROM_OVERLAY, true)
            putExtra(MainActivity.EXTRA_ALERT_TEXT, alertText ?: "")
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
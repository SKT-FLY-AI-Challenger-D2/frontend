package com.example.ytnowplaying

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.ytnowplaying.config.BackendConfig
import com.example.ytnowplaying.data.AnalyzeResponse
import com.example.ytnowplaying.data.BackendClient
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.Severity
import com.example.ytnowplaying.nowplaying.NowPlayingCache
import com.example.ytnowplaying.overlay.OverlayAction
import com.example.ytnowplaying.overlay.OverlayController
import com.example.ytnowplaying.overlay.OverlayPolicy
import com.example.ytnowplaying.overlay.PlaybackStatus
import com.example.ytnowplaying.prefs.ModePrefs
import com.example.ytnowplaying.prefs.MonitoringPrefs
import com.example.ytnowplaying.render.OverlayAlertRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "YTNowPlaying"

// ModePrefs/MonitoringPrefs가 공유하는 SharedPreferences 파일명 — 값이 바뀌면 저쪽도 같이 바꿔야 한다.
private const val PREF_NAME = "ytnowplaying_prefs"

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

    private val POLL_INTERVAL_MS = 5_000L
    private val STOP_BUTTON_GRACE_MS = 1_000L // ✅ 1초 후 꺼짐 (task 살아있을 때만 적용)

    // HIDE 전송용 로컬 dedup: UNKNOWN/SHOW/HIDE 3값(F9). null = UNKNOWN.
    private var lastSentAction: OverlayAction? = null

    // §6.4 task_removal_detected 로그의 전이(alive->removed) 감지 전용 — null=아직 모름.
    // OverlayAction dedup(lastSentAction) 과는 별개 목적이라 필드를 공유하지 않는다.
    private var lastTaskAliveLogged: Boolean? = null
    private var stopButtonRunnable: Runnable? = null

    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val sharedPrefs by lazy {
        applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = BackendClient(BackendConfig.baseUrl)
    private val jobRunner = AnalysisJobRunner<AnalyzeResponse?>(
        scope = scope,
        poster = { block -> mainHandler.post(block) },
    )

    private val renderer by lazy {
        OverlayAlertRenderer(
            appCtx = applicationContext,
            autoDismissMs = 8_000L
        )
    }

    // 5초 self-reschedule 폴링. task 가 없어도 계속 돌며 재실행을 감지한다(V7 — 즉시-통지 경로는 안 둠).
    private val pollingRunnable: Runnable = Runnable {
        reevaluateAndApply()
        mainHandler.postDelayed(pollingRunnable, POLL_INTERVAL_MS)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            // detach 직전 큐에 남아있던 콜백 방어 (F6)
            if (!isAppTaskAlive(applicationContext)) return
            val info = currentController?.let { NowPlayingFetcher.extractFromMediaController(it) }
            if (info == null) {
                Log.d(TAG, "[MediaSession] metadata changed but info=null")
                return
            }
            scheduleConfirm(info)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            reevaluateAndApply()
        }
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.d(TAG, "[YT-SESSION] changed t=${android.os.SystemClock.elapsedRealtime()}")
            reevaluateAndApply()
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListener connected")

        // 재바인딩 시 중복 등록 방지 — 기존 폴링/prefs 리스너를 먼저 제거한 뒤 재등록 (F7)
        mainHandler.removeCallbacks(pollingRunnable)
        prefsListener?.let { sharedPrefs.unregisterOnSharedPreferenceChangeListener(it) }

        msm = getSystemService(MediaSessionManager::class.java)
        val cn = ComponentName(this, YoutubeNowPlayingListenerService::class.java)

        try {
            msm?.addOnActiveSessionsChangedListener(activeSessionsListener, cn, mainHandler)
        } catch (t: Throwable) {
            Log.w(TAG, "addOnActiveSessionsChangedListener failed: ${t.message}")
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reevaluateAndApply() }
        prefsListener = listener
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)

        mainHandler.postDelayed(pollingRunnable, POLL_INTERVAL_MS)

        // attach + 버튼 상태 평가를 한 번에 — attachToYoutubeController()가 F17로 즉시 metadata도
        // 재수집하므로 별도 추출 호출이 필요 없다.
        reevaluateAndApply()
    }

    override fun onListenerDisconnected() {
        disconnectMedia()
        Log.i(TAG, "NotificationListener disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        disconnectMedia()
        scope.cancel()
        super.onDestroy()
    }

    /** 재연결 가능한 정리(F7) — 세션 리스너 해제, 오버레이 숨김, 폴링/prefs 리스너 해제, 미디어 관찰 중단. */
    private fun disconnectMedia() {
        mainHandler.removeCallbacks(pollingRunnable)
        prefsListener?.let { sharedPrefs.unregisterOnSharedPreferenceChangeListener(it) }
        prefsListener = null

        try { msm?.removeOnActiveSessionsChangedListener(activeSessionsListener) } catch (_: Throwable) {}
        msm = null

        stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
        stopButtonRunnable = null
        OverlayController.hide(applicationContext)
        lastSentAction = null // UNKNOWN으로 리셋 — 재연결 시 F9의 "최초 1회 무조건 전송" 규칙이 다시 적용됨

        suspendMediaObservation()
    }

    /** task 제거 시(또는 최종 종료 시) 미디어 감시 자체를 중단한다. 오버레이 적용은 호출측이 맡는다. */
    private fun suspendMediaObservation() {
        detachController()
        cancelHold()
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        pendingStableKey = null
        pendingInfo = null

        jobRunner.cancelActive(restoreDedup = true) { key ->
            if (lastSentKey == key) {
                lastSentKey = null
                lastSentAtMs = 0L
            }
        }

        NowPlayingCache.clear() // F18 — 재실행 직후 옛 영상이 수동분석되지 않도록
    }

    /**
     * 모든 재평가 경로(폴링/재생상태 변경/세션 변경/prefs 변경)가 이 함수 하나만 호출한다.
     * task 가 없으면 grace 를 우회해 즉시 HIDE 적용, 있으면 OverlayPolicy.decide() 결과를
     * grace(HIDE 전환에만)·전송 dedup(F9)을 거쳐 적용한다.
     */
    private fun reevaluateAndApply() {
        val taskAlive = isAppTaskAlive(applicationContext)
        if (!taskAlive) {
            // §6.4 "task 제거 감지 시각" — pollingRunnable 이 task 제거 상태에서도 계속 재스케줄되므로
            // (V7 지원) 매 틱 로그를 남기면 스팸이 된다. alive->removed 전이 시 한 번만 기록한다.
            if (lastTaskAliveLogged != false) {
                FlowLog.taskRemovalDetected("auto", "reevaluateAndApply")
            }
            lastTaskAliveLogged = false

            suspendMediaObservation()
            stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
            stopButtonRunnable = null
            if (lastSentAction != OverlayAction.HIDE) {
                OverlayController.hide(applicationContext)
                lastSentAction = OverlayAction.HIDE
            }
            return
        }
        lastTaskAliveLogged = true

        val cn = ComponentName(this, YoutubeNowPlayingListenerService::class.java)
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

        val playbackStatus = when (currentController?.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
            PlaybackState.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            null -> PlaybackStatus.UNKNOWN
            else -> PlaybackStatus.PAUSED_OR_STOPPED
        }

        val action = OverlayPolicy.decide(
            backgroundModeEnabled = ModePrefs.isBackgroundModeEnabled(applicationContext),
            manualButtonEnabled = MonitoringPrefs.isManualButtonEnabled(applicationContext),
            hasOverlayPermission = Settings.canDrawOverlays(applicationContext),
            playbackStatus = playbackStatus,
        )

        if (action == OverlayAction.SHOW) {
            stopButtonRunnable?.let { mainHandler.removeCallbacks(it) }
            stopButtonRunnable = null
            OverlayController.show(applicationContext) // 매 틱 재전송 — 수신측 멱등(F4), 자가복구
            lastSentAction = OverlayAction.SHOW
        } else {
            if (lastSentAction != OverlayAction.HIDE && stopButtonRunnable == null) {
                val r = Runnable {
                    OverlayController.hide(applicationContext)
                    lastSentAction = OverlayAction.HIDE
                    stopButtonRunnable = null
                }
                stopButtonRunnable = r
                mainHandler.postDelayed(r, STOP_BUTTON_GRACE_MS)
            }
        }
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

        // 재부착 시 현재 재생 정보를 즉시 재수집한다(F17) — 이미 재생 중인 콘텐츠는 새
        // onMetadataChanged 콜백이 안 올 수 있어, 재부착 시점에 직접 한 번 끌어와야 한다.
        NowPlayingFetcher.extractFromMediaController(picked)?.let { scheduleConfirm(it) }

        Log.d(TAG, "Attached to YouTube controller")
    }

    private fun detachController() {
        currentController?.let {
            try { it.unregisterCallback(controllerCallback) } catch (_: Throwable) {}
        }
        currentController = null
    }

    private fun scheduleConfirm(info: NowPlayingInfo) {
        if (!isAppTaskAlive(applicationContext)) return // F6

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
        // 함수 최상단 검사 (F3-b) — task 없으면 캐시·hold 갱신 등 요청 이전 부수효과도 차단
        if (!isAppTaskAlive(applicationContext)) return

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

        val flowId = FlowLog.newFlowId("auto")

        // 요청 시작 직전 검사 (§6.4 삼중 검사 ①)
        val alive1 = isAppTaskAlive(applicationContext)
        FlowLog.event(flowId, "auto", "check1_before_request", alive1)
        if (!alive1) return

        lastSentKey = stableKey
        lastSentAtMs = now2

        jobRunner.submit(
            key = stableKey,
            analyzer = {
                try {
                    backend.analyze(title = info.title, channel = ch, duration = info.duration)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "[Backend] analyze failed", e)
                    null
                }
            },
            onOutcome = { _, apiRes -> handleAnalyzeOutcome(flowId, stableKey, info, ch, apiRes) },
            onDedupRestore = { key ->
                if (lastSentKey == key) {
                    lastSentKey = null
                    lastSentAtMs = 0L
                }
            },
        )
    }

    /**
     * 응답을 저장/표시로 실제 적용하고 applied 여부를 반환한다. 저장이 예외 없이 성공한 뒤,
     * 또는 오류 표시가 정상 실행된 뒤에만 true 를 반환한다(F15) — 응답 수신 직후·저장 직전
     * 두 지점 모두 isAppTaskAlive() 를 재확인한다(§6.4 삼중 검사 ②③).
     */
    private suspend fun handleAnalyzeOutcome(
        flowId: String,
        stableKey: String,
        info: NowPlayingInfo,
        channel: String,
        apiRes: AnalyzeResponse?,
    ): Boolean {
        var applied = false

        if (apiRes == null) {
            withContext(Dispatchers.Main) {
                val alive = isAppTaskAlive(applicationContext)
                FlowLog.event(flowId, "auto", "check_before_comm_error", alive)
                if (!alive) return@withContext
                renderer.showCommError()
                applied = true
            }
            return applied
        }

        // 응답 수신 직후 검사 (§6.4 ②)
        val alive2 = isAppTaskAlive(applicationContext)
        FlowLog.event(flowId, "auto", "check2_after_response", alive2)
        if (!alive2) return false

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
        val summary = if (severity == Severity.NOT_AD) {
            "이 영상은 광고성 콘텐츠가 아닌 일반 정보 전달 영상으로 판단됩니다."
        } else {
            summaryRaw
        }

        val detail = apiRes.analysisReport?.trim().orEmpty().ifBlank { summary }

        val dangerEvidence =
            if (severity == Severity.SAFE || severity == Severity.NOT_AD) emptyList()
            else apiRes.dangerEvidence.orEmpty().map { it.trim() }.filter { it.isNotBlank() }

        val reportId = UUID.randomUUID().toString()
        val report = Report(
            id = reportId,
            detectedAtEpochMs = System.currentTimeMillis(),
            title = info.title,
            channel = channel,
            durationSec = info.duration,
            scorePercent = scorePercent,
            severity = severity,
            dangerEvidence = dangerEvidence,
            summary = summary,
            detail = detail
        )

        withContext(Dispatchers.Main) {
            // 저장 직전 검사 (§6.4 ③)
            val alive3 = isAppTaskAlive(applicationContext)
            FlowLog.event(flowId, "auto", "check3_before_save", alive3)
            if (!alive3) return@withContext

            // §6.4 요구 이벤트: saveReport() 진입 자체를 검사와 별개로 찍는다.
            val aliveSaveEnter = isAppTaskAlive(applicationContext)
            FlowLog.event(flowId, "auto", "save_enter", aliveSaveEnter)

            Log.d(
                TAG,
                "[SAVE] stableKey=$stableKey reportId=$reportId severity=$severity score=$scorePercent summary='${summary.take(80)}'"
            )

            AppContainer.reportRepository.saveReport(report)
            applied = true

            Log.d(TAG, "[SAVE-DONE] reportId=$reportId stableKey=$stableKey")

            // 표시 직전 재검사(신규) — saveReport() 실행 중 task 제거 대응. applied 는 이미
            // true 로 확정됐으므로(저장 자체는 완료됨, F15 dedup 원칙엔 영향 없음) 건드리지
            // 않고, 오직 "제거된 앱에 경고 UI가 뜨는 것"만 여기서 차단한다.
            val aliveDisp = isAppTaskAlive(applicationContext)
            FlowLog.event(flowId, "auto", "check_before_display", aliveDisp)
            if (!aliveDisp) return@withContext

            when (severity) {
                Severity.DANGER -> {
                    renderer.showModal(
                        tone = OverlayAlertRenderer.Tone.DANGER,
                        title = "위험한 영상입니다!",
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

        return applied
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

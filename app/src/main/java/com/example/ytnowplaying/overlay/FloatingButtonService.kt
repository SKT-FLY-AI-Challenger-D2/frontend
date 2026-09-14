package com.example.ytnowplaying.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.example.ytnowplaying.config.BackendConfig
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import com.example.ytnowplaying.AppContainer
import com.example.ytnowplaying.FlowLog
import com.example.ytnowplaying.MainActivity
import com.example.ytnowplaying.data.BackendClient
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.Severity
import com.example.ytnowplaying.isAppTaskAlive
import com.example.ytnowplaying.nowplaying.NowPlayingCache
import com.example.ytnowplaying.render.OverlayAlertRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "REALY_AI"
        private const val AUTO_STOP_AFTER_HIDE_MS = 30_000L
        private const val TASK_POLL_INTERVAL_MS = 5_000L
        private const val NOT_AD_SUMMARY = "이 영상은 광고성 콘텐츠가 아닌 일반 영상으로 판단됩니다."
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var buttonView: View? = null
    private var buttonIcon: TextView? = null
    private var buttonProgress: ProgressBar? = null
    private var added = false

    // ✅ 투명도(요구사항)
    private val BASE_ALPHA = 0.80f
    private val PRESSED_ALPHA = 0.40f
    private val LOADING_ALPHA = 0.50f

    @Volatile
    private var isAnalyzing = false

    // HIDE 유휴 타이머(자원위생, 30초) 예약 여부 — task-poll(신뢰)과는 별개 메커니즘 (계획서 §6.3)
    private var autoStopScheduled = false

    // 진행 중인 수동분석 Job. task 제거 시 이 Job만 취소하고 scope 자체는 살려둔다(계획서 F8).
    private var analysisJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = BackendClient(BackendConfig.baseUrl)

    private val alertRenderer by lazy {
        OverlayAlertRenderer(appCtx = applicationContext, autoDismissMs = 8_000L)
    }

    // 명시적 타입 필요: 이 Runnable이 자기 자신(autoStopRunnable)을 본문에서 참조하므로,
    // 타입을 초기화식에서 추론하게 두면 코틀린이 "recursive problem"으로 컴파일 실패한다.
    private val autoStopRunnable: Runnable = Runnable {
        if (isAnalyzing) {
            // 진행 중인 분석이 있으면 종료를 미루고 같은 지연으로 재예약한다(자원위생 목적,
            // 신뢰 요구와는 무관 — 계획서 §6.3).
            main.postDelayed(autoStopRunnable, AUTO_STOP_AFTER_HIDE_MS)
        } else {
            autoStopScheduled = false
            stopSelf()
        }
    }

    // 5초 self-reschedule task-poll. isAppTaskAlive() 를 직접 조회해 task 제거를 즉시 감지한다
    // (계획서 §6.2 F0, C21 대응 — 배경모드에서도 이 서비스가 떠 있는 동안은 독립적으로 확인).
    private val taskPollRunnable = object : Runnable {
        override fun run() {
            if (!isAppTaskAlive(applicationContext)) {
                android.util.Log.i(TAG, "task removed -> hide + cancel analysis + stopSelf")
                FlowLog.taskRemovalDetected("manual", "taskPollRunnable")
                hideButton()
                analysisJob?.cancel()
                analysisJob = null
                setButtonLoading(false)
                main.removeCallbacks(autoStopRunnable)
                autoStopScheduled = false
                stopSelf()
                return // 서비스가 종료되는 중이므로 재예약하지 않는다.
            }
            main.postDelayed(this, TASK_POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "FloatingButtonService onCreate")
        // onStartCommand()는 SHOW/HIDE마다 반복 호출되므로 폴링은 onCreate()에서만 시작한다
        // (계획서 F10 — 중복 등록 방지).
        main.postDelayed(taskPollRunnable, TASK_POLL_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when {
            OverlayController.isShowAction(action) -> {
                main.removeCallbacks(autoStopRunnable)
                autoStopScheduled = false
                showButton()
            }
            OverlayController.isHideAction(action) -> {
                hideButton()
                if (!autoStopScheduled) {
                    main.postDelayed(autoStopRunnable, AUTO_STOP_AFTER_HIDE_MS)
                    autoStopScheduled = true
                }
                // 이미 예약돼 있으면 그대로 둔다 — added 이전 값을 볼 필요가 없어, 서비스가
                // 신규 생성된 채로 HIDE만 받는 경우에도 타이머가 정확히 한 번 걸린다(계획서 C11).
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(autoStopRunnable)
        main.removeCallbacks(taskPollRunnable)
        setButtonLoading(false)
        hideButton()
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun showButton() {
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.w(TAG, "No overlay permission -> hide + stopSelf")
            Toast.makeText(this, "오버레이 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            hideButton()
            stopSelf()
            return
        }
        addFloatingButton()
    }

    private fun hideButton() {
        // ✅ 배너/모달 모두 제거
        runCatching { alertRenderer.clearAll() }
        removeFloatingButton()
    }

    private fun addFloatingButton() {
        // ⚠️ 가드는 added 단독으로만 건다. wm.addView() 는 호출이 반환돼도 View 가 실제로
        // isAttachedToWindow==true 가 되는 시점은 다음 레이아웃 패스로 밀릴 수 있어(비동기),
        // 매우 짧은 간격(수 ms)으로 SHOW 가 연달아 오면 그 창에서 이 조건이 뚫려 addView() 가
        // 두 번 실행되고 오버레이 창이 중복 생성되는 실결함이 실기기 검증(V8)에서 재현됐다.
        // added 는 addView() 성공 직후 이 함수 안에서 동기적으로 true 가 되고(중간에 다른
        // 메인스레드 메시지가 끼어들 수 없음), removeFloatingButton() 에서만 false 로 돌아오므로
        // 단독 가드로 충분하다.
        if (added) {
            android.util.Log.i(TAG, "button already attached -> skip")
            return
        }

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF4F8DF7.toInt(), 0xFF6E56CF.toInt())
        ).apply { shape = GradientDrawable.OVAL }

        val container = FrameLayout(this).apply {
            background = bg

            val p = dp(18)
            setPadding(p, p, p, p)

            isClickable = true
            isFocusable = false
            ViewCompat.setElevation(this, dp(10).toFloat())

            alpha = BASE_ALPHA

            setOnTouchListener { v, e ->
                if (isAnalyzing) return@setOnTouchListener false
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.alpha = PRESSED_ALPHA
                        v.scaleX = 0.96f
                        v.scaleY = 0.96f
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        v.alpha = BASE_ALPHA
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
                false
            }
        }

        val icon = TextView(this).apply {
            text = "🔍"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            includeFontPadding = false
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                gravity = Gravity.CENTER
            }
        }

        container.addView(icon)
        container.addView(progress)

        container.setOnClickListener { onButtonClicked() }

        try {
            wm.addView(container, buildButtonLayoutParams())
            buttonView = container
            buttonIcon = icon
            buttonProgress = progress
            added = true
            android.util.Log.i(TAG, "wm.addView OK")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "wm.addView FAILED", t)
            added = false
            buttonView = null
            buttonIcon = null
            buttonProgress = null
            runCatching { wm.removeViewImmediate(container) }
        }
    }

    private fun removeFloatingButton() {
        val v = buttonView
        if (v == null) {
            added = false
            return
        }

        try {
            if (v.isAttachedToWindow) wm.removeViewImmediate(v)
        } catch (_: Throwable) {
        } finally {
            added = false
            buttonView = null
            buttonIcon = null
            buttonProgress = null
            isAnalyzing = false
        }
    }

    private fun setButtonLoading(loading: Boolean) {
        main.post {
            // 상태 갱신은 view 존재 여부와 무관하게 항상 먼저 수행한다(계획서 F20-b) — view가
            // 이미 없어도(예: hideButton() 직후) isAnalyzing 은 반드시 갱신돼야 한다.
            isAnalyzing = loading

            val v = buttonView ?: return@post
            val icon = buttonIcon ?: return@post
            val pb = buttonProgress ?: return@post

            v.isEnabled = !loading
            v.alpha = if (loading) LOADING_ALPHA else BASE_ALPHA
            v.scaleX = 1f
            v.scaleY = 1f

            icon.visibility = if (loading) View.INVISIBLE else View.VISIBLE
            pb.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun openReportFromOverlay(reportId: String, alertText: String?) {
        android.util.Log.d(TAG, "[OPEN] reportId=$reportId fromOverlay=true alertLen=${(alertText ?: "").length}")
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

    private fun onButtonClicked() {
        if (isAnalyzing) return

        val snap = NowPlayingCache.get()
        if (snap == null) {
            Toast.makeText(this, "재생 중인 영상 정보를 아직 못 가져왔습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val flowId = FlowLog.newFlowId("manual")

        // 요청 시작 직전 검사 (계획서 §6.4 삼중 검사 ①)
        val alive1 = isAppTaskAlive(applicationContext)
        FlowLog.event(flowId, "manual", "check1_before_request", alive1)
        if (!alive1) return

        setButtonLoading(true)

        val job = scope.launch {
            try {
                delay(700L)

                val apiRes = try {
                    backend.analyze(
                        title = snap.title,
                        channel = snap.channel,
                        duration = snap.duration
                    )
                } catch (e: CancellationException) {
                    // task 제거로 인한 정상적인 취소 — 통신 오류로 취급하지 않고 그대로 전파한다.
                    throw e
                } catch (e: Exception) {
                    null
                }

                // 응답 수신 직후 검사 (계획서 §6.4 삼중 검사 ②) — 에러 응답 표시 전에도 적용
                val alive2 = isAppTaskAlive(applicationContext)
                FlowLog.event(flowId, "manual", "check2_after_response", alive2)
                if (!alive2) return@launch

                if (apiRes == null) {
                    withContext(Dispatchers.Main) {
                        // 표시 직전 재검사(신규) — Main 디스패처 전환 사이 task 제거 대응
                        val aliveErr = isAppTaskAlive(applicationContext)
                        FlowLog.event(flowId, "manual", "check_before_comm_error", aliveErr)
                        if (!aliveErr) return@withContext
                        alertRenderer.showCommError(
                            title = "죄송합니다",
                            message = "통신 오류가 발생했습니다.\n다시 돋보기 버튼을 눌러주세요.",
                            buttonText = "확인",
                            autoDismissOverrideMs = 0L
                        )
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
                val summary = if (severity == Severity.NOT_AD) {
                    NOT_AD_SUMMARY
                } else {
                    summaryRaw
                }

                val detail = if (severity == Severity.NOT_AD) {
                    ""
                } else {
                    apiRes.analysisReport?.trim().orEmpty().ifBlank { summary }
                }

                val dangerEvidence =
                    if (severity == Severity.SAFE || severity == Severity.NOT_AD) emptyList()
                    else apiRes.dangerEvidence.orEmpty().map { it.trim() }.filter { it.isNotBlank() }

                val reportId = UUID.randomUUID().toString()
                val report = Report(
                    id = reportId,
                    detectedAtEpochMs = System.currentTimeMillis(),
                    title = snap.title,
                    channel = snap.channel,
                    durationSec = snap.duration,
                    scorePercent = scorePercent,
                    severity = severity,
                    dangerEvidence = dangerEvidence,
                    summary = summary,
                    detail = detail
                )

                // 저장 직전 검사 (계획서 §6.4 삼중 검사 ③)
                val alive3 = isAppTaskAlive(applicationContext)
                FlowLog.event(flowId, "manual", "check3_before_save", alive3)
                if (!alive3) return@launch

                // §6.4 요구 이벤트: saveReport() 진입 자체를 검사와 별개로 찍어, 검사~저장호출
                // 사이 예상 밖 지연(suspend 재스케줄 등)이 있었는지 로그만으로 재구성 가능하게 한다.
                val aliveSaveEnter = isAppTaskAlive(applicationContext)
                FlowLog.event(flowId, "manual", "save_enter", aliveSaveEnter)

                // ✅ 저장은 IO(현재 코루틴 컨텍스트)에서 수행
                AppContainer.reportRepository.saveReport(report)

                // ✅ 오버레이/Activity는 Main에서 처리
                withContext(Dispatchers.Main) {
                    // 표시 직전 재검사(신규) — saveReport() 실행 중 task 제거 대응. 저장 자체는
                    // 이미 완료됐으므로(F12 dedup 원칙과 무관, 수동분석엔 dedup 복원 대상이 없음)
                    // 여기서 막는 건 오직 "제거된 앱의 결과 UI가 뜨는 것"만 차단하기 위함이다.
                    val aliveDisp = isAppTaskAlive(applicationContext)
                    FlowLog.event(flowId, "manual", "check_before_display", aliveDisp)
                    if (!aliveDisp) return@withContext

                    when (severity) {
                        Severity.DANGER -> {
                            alertRenderer.showModal(
                                tone = OverlayAlertRenderer.Tone.DANGER,
                                title = "위험한 영상입니다!",
                                bodyLead = report.summary,
                                autoDismissOverrideMs = 30_000L,
                            ) {
                                openReportFromOverlay(reportId = reportId, alertText = report.summary)
                            }
                        }

                        Severity.CAUTION -> {
                            alertRenderer.showModal(
                                tone = OverlayAlertRenderer.Tone.CAUTION,
                                title = "주의가 필요합니다!",
                                bodyLead = report.summary,
                                autoDismissOverrideMs = 8_000L,
                            ) {
                                openReportFromOverlay(reportId = reportId, alertText = report.summary)
                            }
                        }

                        Severity.SAFE -> {
                            alertRenderer.showBanner(
                                tone = OverlayAlertRenderer.Tone.SAFE,
                                title = "안전",
                                subtitle = "탭하여 보고서 보기",
                                autoDismissMs = 2_000L,
                            ) {
                                openReportFromOverlay(reportId = reportId, alertText = report.summary)
                            }
                        }

                        Severity.NOT_AD -> {
                            alertRenderer.showBanner(
                                tone = OverlayAlertRenderer.Tone.NOT_AD,
                                title = "광고 아님",
                                subtitle = "탭하여 보고서 보기",
                                autoDismissMs = 2_000L,
                            ) {
                                openReportFromOverlay(reportId = reportId, alertText = report.summary)
                            }
                        }
                    }
                }
            } finally {
                setButtonLoading(false)
            }
        }
        analysisJob = job
        job.invokeOnCompletion {
            main.post {
                if (analysisJob === job) analysisJob = null
            }
        }
    }

    private fun buildButtonLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(24)
            y = 0
        }
    }
}
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
import com.example.ytnowplaying.MainActivity
import com.example.ytnowplaying.data.BackendClient
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.Severity
import com.example.ytnowplaying.nowplaying.NowPlayingCache
import com.example.ytnowplaying.render.OverlayAlertRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = BackendClient(BackendConfig.baseUrl)

    private val alertRenderer by lazy {
        OverlayAlertRenderer(appCtx = applicationContext, autoDismissMs = 8_000L)
    }

    private val autoStopRunnable = Runnable {
        if (!added) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "FloatingButtonService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when {
            OverlayController.isShowAction(action) -> {
                main.removeCallbacks(autoStopRunnable)
                showButton()
            }
            OverlayController.isHideAction(action) -> {
                hideButton()
                main.removeCallbacks(autoStopRunnable)
                main.postDelayed(autoStopRunnable, AUTO_STOP_AFTER_HIDE_MS)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(autoStopRunnable)
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
        if (added && buttonView?.isAttachedToWindow == true) {
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
            val v = buttonView ?: return@post
            val icon = buttonIcon ?: return@post
            val pb = buttonProgress ?: return@post

            isAnalyzing = loading

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

        setButtonLoading(true)

        scope.launch {
            try {
                delay(700L)

                val apiRes = runCatching {
                    backend.analyze(
                        title = snap.title,
                        channel = snap.channel,
                        duration = snap.duration
                    )
                }.getOrNull()

                if (apiRes == null) {
                    withContext(Dispatchers.Main) {
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

                // ✅ 저장은 IO(현재 코루틴 컨텍스트)에서 수행
                AppContainer.reportRepository.saveReport(report)

                // ✅ 오버레이/Activity는 Main에서 처리
                withContext(Dispatchers.Main) {
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
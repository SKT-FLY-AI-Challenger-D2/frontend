package com.example.ytnowplaying.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import com.example.ytnowplaying.MainActivity
import com.example.ytnowplaying.data.BackendClient
import com.example.ytnowplaying.nowplaying.NowPlayingCache
import com.example.ytnowplaying.render.OverlayAlertRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.TextView
import android.graphics.Typeface


class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "REALLY_AI"
        private const val EXTRA_OPEN_REPORT = "open_report" // (현재 미사용이어도 둬도 됨)
    }

    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    // 기존: private var buttonView: ImageView? = null
    private var buttonView: View? = null
    private var added = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 백엔드 (에뮬레이터 기준)
    private val backend = BackendClient("http://10.0.2.2:8000/")

    // 경고 오버레이
    private val alertRenderer by lazy {
        OverlayAlertRenderer(
            appCtx = applicationContext,
            autoDismissMs = 8_000L
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "FloatingButtonService onCreate")

        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.w(TAG, "No overlay permission -> stopSelf")
            Toast.makeText(this, "오버레이 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        addFloatingButton()
    }

    override fun onDestroy() {
        removeFloatingButton()
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun addFloatingButton() {
        android.util.Log.i(TAG, "addFloatingButton called")

        // 이미 attach 되어있으면(added 플래그가 꼬여도) 재추가 금지
        buttonView?.let { existing ->
            if (existing.isAttachedToWindow) {
                added = true
                android.util.Log.i(TAG, "buttonView already attached -> skip")
                return
            }
        }

        // 재진입 방지
        if (added) {
            android.util.Log.i(TAG, "already added flag -> skip")
            return
        }
        added = true

        // addFloatingButton() 안의 iv 생성부를 이걸로 교체
        val tv = TextView(this).apply {
            text = "🔍"
            // 이모지는 폰트별로 크기 체감이 달라서 SP를 조금 키우는 게 보통 좋음
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            includeFontPadding = false

            // 원형 그라데이션 배경은 동일
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    0xFF4F8DF7.toInt(),
                    0xFF6E56CF.toInt()
                )
            ).apply { shape = GradientDrawable.OVAL }

            val p = dp(18)
            setPadding(p, p, p, p)

            ViewCompat.setElevation(this, dp(10).toFloat())
            setOnClickListener { onButtonClicked() }
        }

        buttonView = tv

        try {
            wm.addView(tv, buildButtonLayoutParams())
            android.util.Log.i(TAG, "wm.addView OK")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "wm.addView FAILED", t)
            added = false
            buttonView = null
            runCatching { wm.removeViewImmediate(tv) }
        }

    }

    private fun removeFloatingButton() {
        if (!added) return
        val v = buttonView
        try {
            if (v != null) wm.removeViewImmediate(v)
        } catch (_: Throwable) {
        } finally {
            added = false
            buttonView = null
        }
    }

    private fun openReportFromOverlay(
        reportId: String,
        alertText: String?
    ) {
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

    private fun onButtonClicked() {
        val snap = NowPlayingCache.get()
        if (snap == null) {
            Toast.makeText(this, "재생 중인 영상 정보를 아직 못 가져왔습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            // 요구사항: 버튼 누르면 잠시 후 딜레이
            delay(700L)

            // 여기서만 백엔드 전송
            val resultText: String? = runCatching {
                backend.search(
                    videoKey = snap.stableKey,
                    title = snap.title,
                    channel = snap.channel
                )
            }.getOrNull()

            // ✅ report 화면에 넘길 텍스트(임시): 백엔드 응답 없으면 기본 문구
            val alertText: String = resultText?.takeIf { it.isNotBlank() }
                ?: "! 영상에 문제가 있습니다"

            withContext(Dispatchers.Main) {
                // 오버레이 표시(문구는 요구사항 고정)
                // 탭하면 앱 열고 Report로 이동(임시 reportId=demo)
                alertRenderer.showWarning("! 영상에 문제가 있습니다") {
                    openReportFromOverlay(reportId = "demo", alertText = alertText)
                }
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
            gravity = Gravity.END or Gravity.CENTER_VERTICAL // 오른쪽 중간
            x = dp(24)
            y = 0
        }
    }
}

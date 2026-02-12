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
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
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

class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "REALY_AI"
        private const val AUTO_STOP_AFTER_HIDE_MS = 30_000L // hide 후 30초 지나면 서비스 정리
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var buttonView: View? = null
    private var added = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = BackendClient("http://10.0.2.2:8000/")

    private val alertRenderer by lazy {
        OverlayAlertRenderer(
            appCtx = applicationContext,
            autoDismissMs = 8_000L
        )
    }

    private val autoStopRunnable = Runnable {
        // 버튼이 안 떠있으면 서비스 종료(리소스 정리)
        if (!added) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "FloatingButtonService onCreate")
        // 여기서 add하지 말고, onStartCommand에서 action에 따라 show/hide
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
        // 경고 모달까지 같이 떠있을 수 있으면 정리(원치 않으면 제거해도 됨)
        runCatching { alertRenderer.clearWarning() }
        removeFloatingButton()
    }

    private fun addFloatingButton() {
        if (added && buttonView?.isAttachedToWindow == true) {
            android.util.Log.i(TAG, "button already attached -> skip")
            return
        }

        val tv = TextView(this).apply {
            text = "🔍"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            includeFontPadding = false

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF4F8DF7.toInt(), 0xFF6E56CF.toInt())
            ).apply { shape = GradientDrawable.OVAL }

            val p = dp(18)
            setPadding(p, p, p, p)

            ViewCompat.setElevation(this, dp(10).toFloat())
            setOnClickListener { onButtonClicked() }
        }

        try {
            wm.addView(tv, buildButtonLayoutParams())
            buttonView = tv
            added = true
            android.util.Log.i(TAG, "wm.addView OK")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "wm.addView FAILED", t)
            added = false
            buttonView = null
            runCatching { wm.removeViewImmediate(tv) }
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

    private fun onButtonClicked() {
        val snap = NowPlayingCache.get()
        if (snap == null) {
            Toast.makeText(this, "재생 중인 영상 정보를 아직 못 가져왔습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            delay(700L)

            val resultText: String? = runCatching {
                backend.search(
                    videoKey = snap.stableKey,
                    title = snap.title,
                    channel = snap.channel,
                    duration = snap.duration
                )
            }.getOrNull()

            val alertText: String = resultText?.takeIf { it.isNotBlank() }
                ?: "! 영상에 문제가 있습니다"

            withContext(Dispatchers.Main) {
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
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(24)
            y = 0
        }
    }
}

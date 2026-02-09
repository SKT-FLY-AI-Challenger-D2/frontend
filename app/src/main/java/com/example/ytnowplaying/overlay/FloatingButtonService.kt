package com.example.ytnowplaying.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
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
import com.example.ytnowplaying.MainActivity
class FloatingButtonService : Service() {

    companion object {
        private const val EXTRA_OPEN_REPORT = "open_report"
    }

    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var buttonView: ImageView? = null
    private var added = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 백엔드 (에뮬레이터 기준)
    private val backend = BackendClient("http://10.0.2.2:8000/")

    // 경고 오버레이 (✅ onTap은 생성자에 넣지 말고 showWarning에서 넣는다)
    private val alertRenderer by lazy {
        // 생성자 시그니처에 맞게: (Context, autoDismissMs) 형태라면 아래처럼 사용
        OverlayAlertRenderer(
            appCtx = applicationContext,
            autoDismissMs = 8_000L
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("XXX_AI", "FloatingButtonService onCreate")

        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.w("XXX_AI", "No overlay permission -> stopSelf")
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

    private fun addFloatingButton() {
        android.util.Log.i("XXX_AI", "addFloatingButton called")

        // 이미 attach 되어있으면(added 플래그가 꼬여도) 재추가 금지
        buttonView?.let { existing ->
            if (existing.isAttachedToWindow) {
                added = true
                android.util.Log.i("XXX_AI", "buttonView already attached -> skip")
                return
            }
        }

        // 재진입 방지(핵심): addView 전에 먼저 잠금
        if (added) {
            android.util.Log.i("XXX_AI", "already added flag -> skip")
            return
        }
        added = true

        val iv = android.widget.ImageView(this).apply {
            // 가시성 확보용(임시)
            setImageResource(android.R.drawable.ic_dialog_alert)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xCC111111.toInt())
            setOnClickListener { onButtonClicked() }
        }

        buttonView = iv

        try {
            wm.addView(iv, buildButtonLayoutParams())
            android.util.Log.i("XXX_AI", "wm.addView OK")
        } catch (t: Throwable) {
            android.util.Log.e("XXX_AI", "wm.addView FAILED", t)

            // 실패 시 롤백(다음에 다시 시도 가능하게)
            added = false
            buttonView = null

            // 혹시 반쯤 붙은 케이스까지 정리 시도
            runCatching { wm.removeViewImmediate(iv) }
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
            delay(700L)

            // 여기서만 백엔드 전송
            val resultText: String? = runCatching {
                backend.search(
                    videoKey = snap.stableKey,
                    title = snap.title,
                    channel = snap.channel
                )
            }.getOrNull()

            // ✅ 여기서 alertText를 “반드시” 만든다
            val alertText: String = resultText?.takeIf { it.isNotBlank() }
                ?: "! 영상에 문제가 있습니다"

            withContext(Dispatchers.Main) {
                // ✅ 오버레이 문구 고정 + 탭하면 앱 열기
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
            gravity = Gravity.END or Gravity.CENTER_VERTICAL // ✅ 오른쪽 중간
            x = 24
            y = 0
        }
    }
}

package com.example.ytnowplaying.render

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

private const val TAG = "OverlayRenderer"

class OverlayAlertRenderer(
    private val appCtx: Context,
    private val autoDismissMs: Long = 8_000L, // 0이면 자동 제거 비활성
) : AlertRenderer {

    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var textView: TextView? = null
    private var closeView: TextView? = null
    private var isAdded = false

    private var lastOnTap: (() -> Unit)? = null
    private val removeRunnable = Runnable { clearWarning() }

    override fun showWarning(text: String, onTap: (() -> Unit)?) {
        if (!Settings.canDrawOverlays(appCtx)) {
            Log.w(TAG, "No overlay permission.")
            return
        }

        lastOnTap = onTap
        ensureView()

        textView?.text = text

        // 탭 동작: onTap 있으면 실행, 없으면 닫기
        rootView?.setOnClickListener {
            val cb = lastOnTap
            if (cb != null) cb() else clearWarning()
        }

        // 닫기(X)는 항상 닫기
        closeView?.setOnClickListener { clearWarning() }

        if (!isAdded) {
            try {
                wm.addView(rootView, buildLayoutParams())
                isAdded = true
            } catch (t: Throwable) {
                Log.e(TAG, "addView failed: ${t.message}", t)
                isAdded = false
            }
        }

        // 자동 제거 타이머 갱신
        main.removeCallbacks(removeRunnable)
        if (autoDismissMs > 0L) {
            main.postDelayed(removeRunnable, autoDismissMs)
        }
    }

    override fun clearWarning() {
        main.removeCallbacks(removeRunnable)
        lastOnTap = null

        if (!isAdded) return
        try {
            wm.removeView(rootView)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed: ${t.message}")
        } finally {
            isAdded = false
        }
    }

    private fun ensureView() {
        if (rootView != null) return

        val container = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xEE111111.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        val tv = TextView(appCtx).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val x = TextView(appCtx).apply {
            text = "✕" // 리소스 없이 닫기
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 0, 0, 0)
        }

        container.addView(tv)
        container.addView(x)

        textView = tv
        closeView = x
        rootView = container
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 0
        }
    }
}

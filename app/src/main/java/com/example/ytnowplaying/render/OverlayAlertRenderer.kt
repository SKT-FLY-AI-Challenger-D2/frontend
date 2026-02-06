package com.example.ytnowplaying.render

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

private const val TAG = "OverlayRenderer"

class OverlayAlertRenderer(private val appCtx: Context) : AlertRenderer {

    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var textView: TextView? = null
    private var isAdded = false

    override fun showWarning(text: String) {
        if (!Settings.canDrawOverlays(appCtx)) {
            Log.w(TAG, "No overlay permission. Ask user to enable 'Draw over other apps'.")
            return
        }

        if (rootView == null) {
            val tv = TextView(appCtx).apply {
                setPadding(32, 24, 32, 24)
                textSize = 14f
                // 배경/테두리 최소 구성 (리소스 없이)
                setBackgroundColor(0xEE111111.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { clearWarning() } // 탭하면 닫기
            }
            textView = tv
            rootView = tv
        }

        textView?.text = text

        if (!isAdded) {
            try {
                wm.addView(rootView, buildLayoutParams())
                isAdded = true
            } catch (t: Throwable) {
                Log.e(TAG, "addView failed: ${t.message}", t)
                isAdded = false
            }
        }
    }

    override fun clearWarning() {
        if (!isAdded) return
        try {
            wm.removeView(rootView)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed: ${t.message}")
        } finally {
            isAdded = false
        }
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

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
import android.widget.FrameLayout
import android.widget.TextView

private const val TAG = "OverlayRenderer"

class OverlayAlertRenderer(
    private val appCtx: Context,
    private val autoDismissMs: Long = 20_000L, // ✅ 자동 제거 시간(원하면 변경)
) : AlertRenderer {

    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var rootView: View? = null          // FrameLayout
    private var textView: TextView? = null      // 본문
    private var closeView: TextView? = null     // X 버튼
    private var isAdded = false

    private val removeRunnable = Runnable { clearWarning() }

    override fun showWarning(text: String) {
        // ✅ 안전하게 항상 메인에서 처리
        main.post {
            if (!Settings.canDrawOverlays(appCtx)) {
                Log.w(TAG, "No overlay permission. Ask user to enable 'Draw over other apps'.")
                return@post
            }

            if (rootView == null) {
                // ✅ 컨테이너 생성 (본문 + 닫기 버튼)
                val container = FrameLayout(appCtx).apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundColor(0xEE111111.toInt())
                }

                val tv = TextView(appCtx).apply {
                    textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt())
                }

                val close = TextView(appCtx).apply {
                    this.text = "✕"// 리소스 없이 닫기
                    textSize = 18f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    setOnClickListener { clearWarning() }
                }

                // 본문: 오른쪽에 X 자리 확보
                container.addView(
                    tv,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(32)
                    }
                )

                // 닫기 버튼: 우상단
                container.addView(
                    close,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END or Gravity.TOP
                    }
                )

                // 기존 “탭하면 닫기” 유지하고 싶으면 컨테이너에 리스너 유지
                container.setOnClickListener { clearWarning() }

                textView = tv
                closeView = close
                rootView = container
            }

            textView?.text = text

            if (!isAdded) {
                try {
                    wm.addView(rootView, buildLayoutParams())
                    isAdded = true
                } catch (t: Throwable) {
                    Log.e(TAG, "addView failed: ${t.message}", t)
                    isAdded = false
                    return@post
                }
            }

            // ✅ 자동 제거 타이머 리셋
            main.removeCallbacks(removeRunnable)
            main.postDelayed(removeRunnable, autoDismissMs)
        }
    }

    override fun clearWarning() {
        main.post {
            // ✅ 타이머 취소
            main.removeCallbacks(removeRunnable)

            if (!isAdded) return@post
            try {
                wm.removeView(rootView)
            } catch (t: Throwable) {
                Log.w(TAG, "removeView failed: ${t.message}")
                // 필요하면 immediate로 강제
                runCatching { wm.removeViewImmediate(rootView) }
            } finally {
                isAdded = false
            }
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

    private fun dp(v: Int): Int =
        (v * appCtx.resources.displayMetrics.density).toInt()
}

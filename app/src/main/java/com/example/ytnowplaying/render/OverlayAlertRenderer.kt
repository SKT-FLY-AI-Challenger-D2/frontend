package com.example.ytnowplaying.render

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private const val TAG = "OverlayRenderer"

class OverlayAlertRenderer(
    private val appCtx: Context,
    private val autoDismissMs: Long = 8_000L, // 0이면 자동 제거 비활성
) : AlertRenderer {

    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var rootView: View? = null              // full-screen dim layer
    private var cardView: View? = null              // white card
    private var titleView: TextView? = null         // red title
    private var bodyView: TextView? = null          // gray description
    private var actionView: TextView? = null        // "탭하여 보고서 보기"
    private var closeView: TextView? = null         // X button
    private var isAdded = false

    private var lastOnTap: (() -> Unit)? = null
    private val removeRunnable = Runnable { clearWarning() }

    override fun showWarning(
        title: String,
        bodyLead: String?,
        onTap: (() -> Unit)?
    ) {
        if (!Settings.canDrawOverlays(appCtx)) {
            Log.w(TAG, "No overlay permission.")
            return
        }

        lastOnTap = onTap
        ensureView()

        // 제목
        titleView?.text = title

        // 본문(선제 요약 1줄 + 안내 1줄)
        val lead = bodyLead?.trim().takeIf { !it.isNullOrBlank() }
            ?: "이 영상은 조작되었을 가능성이 있습니다."
        bodyView?.text = "$lead\n자세한 분석 결과를 확인하세요."

        // 딤 영역 탭: 닫기 (모달 UX)
        rootView?.setOnClickListener { clearWarning() }

        cardView?.setOnClickListener {
            val cb = lastOnTap
            clearWarning()              // ✅ 먼저 닫기
            cb?.invoke()                // 그 다음 이동
        }

        // 하단 문구도 동일
        actionView?.setOnClickListener {
            val cb = lastOnTap
            clearWarning()              // ✅ 먼저 닫기
            cb?.invoke()
        }

        // X 버튼: 항상 닫기
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
            wm.removeViewImmediate(rootView)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed: ${t.message}")
        } finally {
            isAdded = false
        }
    }

    private fun ensureView() {
        if (rootView != null) return

        // dp helper
        fun dp(v: Int): Int = (v * appCtx.resources.displayMetrics.density).toInt()
        fun dpF(v: Float): Float = (v * appCtx.resources.displayMetrics.density)

        // 화면 폭에 맞춰 "오른쪽 스샷처럼 크게" (대략 80% 폭, 최대 340dp)
        val dm = appCtx.resources.displayMetrics
        val cardWidthPx = minOf((dm.widthPixels * 0.80f).toInt(), dp(340))

        // 1) 전체 딤 레이어 (풀스크린)
        val dimRoot = FrameLayout(appCtx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x99000000.toInt()) // dim
            isClickable = true
        }

        // 2) 흰색 카드 (중앙)
        val card = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(20)
            val padV = dp(18)
            setPadding(padH, padV, padH, padV)

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(18f)
                setColor(0xFFFFFFFF.toInt())
            }

            layoutParams = FrameLayout.LayoutParams(cardWidthPx, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true // dimRoot 클릭과 분리
        }

        // 상단: X 버튼 영역(우측 상단)
        val topRow = FrameLayout(appCtx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val x = TextView(appCtx).apply {
            text = "✕"
            textSize = 16f
            setTextColor(0xFF333333.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
            }
        }
        topRow.addView(x)

        // 아이콘 원형 배경 + 경고 아이콘
        val iconCircle = FrameLayout(appCtx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(18)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FF3B30.toInt()) // 연한 빨강(핑크)
            }
        }

        val icon = ImageView(appCtx).apply {
            // 시스템 기본 아이콘 사용(리소스 추가 없이)
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(0xFFFF3B30.toInt())
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).apply {
                gravity = Gravity.CENTER
            }
        }
        iconCircle.addView(icon)

        // 제목(빨강)
        val title = TextView(appCtx).apply {
            textSize = 22f
            setTextColor(0xFFFF3B30.toInt())
            setPadding(0, dp(12), 0, dp(18))
            gravity = Gravity.CENTER
            // bold
            paint.isFakeBoldText = true
        }

        // 본문(회색)
        val body = TextView(appCtx).apply {
            textSize = 13.5f
            setTextColor(0xFF6B7280.toInt())
            gravity = Gravity.CENTER
        }

        // 액션 텍스트(하단)
        val action = TextView(appCtx).apply {
            text = "탭하여 보고서 보기"
            textSize = 12.5f
            setTextColor(0xFF6B7280.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(12))
        }

        card.addView(topRow)
        card.addView(iconCircle)
        card.addView(title)
        card.addView(body)
        card.addView(action)

        dimRoot.addView(card)

        rootView = dimRoot
        cardView = card
        closeView = x
        titleView = title
        bodyView = body
        actionView = action
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
            WindowManager.LayoutParams.MATCH_PARENT, // ✅ 풀스크린 딤 + 중앙 카드
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }
}

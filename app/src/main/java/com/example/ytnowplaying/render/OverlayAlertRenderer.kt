package com.example.ytnowplaying.render

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import androidx.core.view.ViewCompat
import com.example.ytnowplaying.R

private const val TAG = "OverlayRenderer"

class OverlayAlertRenderer(
    private val appCtx: Context,
    private val autoDismissMs: Long = 8_000L,
) : AlertRenderer {

    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    // -------------------- Modal(딤+카드) --------------------
    private var rootView: View? = null
    private var cardView: View? = null
    private var iconCircleBg: GradientDrawable? = null
    private var iconView: ImageView? = null
    private var titleView: TextView? = null
    private var bodyView: TextView? = null
    private var actionView: TextView? = null
    private var closeView: TextView? = null
    private var isModalAdded = false
    private var modalOnTap: (() -> Unit)? = null
    private val removeModalRunnable = Runnable { clearWarning() }

    // -------------------- Banner(상단 토스트형) --------------------
    private var bannerView: View? = null
    private var bannerBg: GradientDrawable? = null
    private var bannerBadgeBg: GradientDrawable? = null

    private var bannerSymbolView: TextView? = null
    private var bannerSymbolIconView: ImageView? = null

    private var bannerTitleView: TextView? = null
    private var bannerSubtitleView: TextView? = null
    private var bannerCloseView: TextView? = null
    private var isBannerAdded = false
    private var bannerOnTap: (() -> Unit)? = null
    private var bannerWidthPx: Int = 0
    private val removeBannerRunnable = Runnable { clearBanner() }

    enum class Tone { DANGER, CAUTION, SAFE, NOT_AD }

    private data class Palette(
        val accent: Int,
        val circleBg: Int,
        val bannerBg: Int,
        val badgeBg: Int,
        val bannerText: Int,
        val bannerSubText: Int,
        val symbol: String,
        val defaultLead: String,
    )

    private fun paletteOf(tone: Tone): Palette {
        return when (tone) {
            Tone.DANGER -> Palette(
                accent = 0xFFFF3B30.toInt(),
                circleBg = 0x33FF3B30.toInt(),
                bannerBg = 0xFFFFE4E6.toInt(),
                badgeBg = 0x33DC2626.toInt(),
                bannerText = 0xFFDC2626.toInt(),
                bannerSubText = 0xFF7F1D1D.toInt(),
                symbol = "⚠",
                defaultLead = "이 영상은 조작되었을 가능성이 있습니다.",
            )
            Tone.CAUTION -> Palette(
                accent = 0xFFFF8A00.toInt(),
                circleBg = 0x33FF8A00.toInt(),
                bannerBg = 0xFFFFF3E6.toInt(),
                badgeBg = 0x33EA580C.toInt(),
                bannerText = 0xFFEA580C.toInt(),
                bannerSubText = 0xFF7C2D12.toInt(),
                symbol = "⚠",
                defaultLead = "이 영상에는 의심스러운 요소가 있습니다.",
            )
            Tone.SAFE -> Palette(
                accent = 0xFF16A34A.toInt(),
                circleBg = 0x3316A34A.toInt(),
                bannerBg = 0xFFFFFFFF.toInt(),
                badgeBg = 0xFFDCFCE7.toInt(),
                bannerText = 0xFF16A34A.toInt(),
                bannerSubText = 0xFF14532D.toInt(),
                symbol = "✓",
                defaultLead = "뚜렷한 의심 요소가 발견되지 않았습니다.",
            )
            Tone.NOT_AD -> Palette(
                accent = 0xFF2563EB.toInt(),
                circleBg = 0x332563EB.toInt(),
                bannerBg = 0xFFFFFFFF.toInt(),
                badgeBg = 0xFFDBEAFE.toInt(),
                bannerText = 0xFF2563EB.toInt(),
                bannerSubText = 0xFF1E3A8A.toInt(),
                symbol = "🛡",
                defaultLead = "이 영상은 광고성 콘텐츠가 아닌 것으로 보입니다.",
            )
        }
    }

    private fun dp(v: Int): Int = (v * appCtx.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = (v * appCtx.resources.displayMetrics.density)
    private fun alphaColor(color: Int, a: Int): Int = ((a and 0xFF) shl 24) or (color and 0x00FFFFFF)

    private fun statusBarHeightPx(): Int {
        val resId = appCtx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) appCtx.resources.getDimensionPixelSize(resId) else 0
    }

    // ------------------------------------------------------------
    // AlertRenderer (기존 호환)
    // ------------------------------------------------------------

    override fun showWarning(title: String, bodyLead: String?, onTap: (() -> Unit)?) {
        showModal(
            tone = Tone.DANGER,
            title = title,
            bodyLead = bodyLead,
            autoDismissOverrideMs = autoDismissMs,
            onTap = onTap
        )
    }

    override fun clearWarning() {
        main.removeCallbacks(removeModalRunnable)
        modalOnTap = null

        if (!isModalAdded) return
        try {
            wm.removeViewImmediate(rootView)
        } catch (t: Throwable) {
            Log.w(TAG, "remove modal failed: ${t.message}")
        } finally {
            isModalAdded = false
        }
    }

    fun clearBanner() {
        main.removeCallbacks(removeBannerRunnable)
        bannerOnTap = null

        if (!isBannerAdded) return
        try {
            wm.removeViewImmediate(bannerView)
        } catch (t: Throwable) {
            Log.w(TAG, "remove banner failed: ${t.message}")
        } finally {
            isBannerAdded = false
        }
    }

    fun clearAll() {
        clearWarning()
        clearBanner()
    }

    // ------------------------------------------------------------
    // New APIs
    // ------------------------------------------------------------

    /**
     * 위험/주의 모달
     * - title 위치에 쇼트 리포트(= bodyLead)를 출력
     * - 그 아래에 “자세한 분석 결과를 확인하세요.”
     * - 하단 “탭하여 보고서 보기”는 유지
     */
    fun showModal(
        tone: Tone,
        title: String,
        bodyLead: String? = null,
        autoDismissOverrideMs: Long = autoDismissMs,
        onTap: (() -> Unit)? = null,
    ) {
        if (!Settings.canDrawOverlays(appCtx)) {
            Log.w(TAG, "No overlay permission.")
            return
        }

        clearBanner()

        modalOnTap = onTap
        ensureModalView()

        val p = paletteOf(tone)

        iconCircleBg?.setColor(p.circleBg)
        iconView?.setColorFilter(p.accent)
        titleView?.setTextColor(p.accent)

        // ✅ 액션은 기존 “링크형”으로 복구
        applyActionAsLink()
        actionView?.text = "탭하여 보고서 보기"

        // ✅ 변경: title 위치에 쇼트 리포트 출력(없으면 title, 그래도 없으면 defaultLead)
        val lead = bodyLead?.trim().takeIf { !it.isNullOrBlank() }
            ?: title.trim().takeIf { it.isNotBlank() }
            ?: p.defaultLead

        titleView?.text = lead
        bodyView?.text = "자세한 분석 결과를 확인하세요."

        rootView?.setOnClickListener { clearWarning() }

        val openCb = {
            val cb = modalOnTap
            clearWarning()
            cb?.invoke()
        }
        cardView?.setOnClickListener { openCb() }
        actionView?.setOnClickListener { openCb() }
        closeView?.setOnClickListener { clearWarning() }

        if (!isModalAdded) {
            try {
                wm.addView(rootView, buildModalLayoutParams())
                isModalAdded = true
            } catch (t: Throwable) {
                Log.e(TAG, "add modal failed: ${t.message}", t)
                isModalAdded = false
            }
        }

        main.removeCallbacks(removeModalRunnable)
        if (autoDismissOverrideMs > 0L) {
            main.postDelayed(removeModalRunnable, autoDismissOverrideMs)
        }
    }

    /**
     * ✅ 통신 오류 모달(요구사항)
     * - 제목: "죄송합니다"
     * - 본문: 통신 오류 안내
     * - 버튼: "확인"(닫기)
     * - X/딤 탭도 닫기
     */
    fun showCommError(
        title: String = "죄송합니다",
        message: String = "통신 오류가 발생했습니다.\n다시 돋보기 버튼을 눌러주세요.",
        buttonText: String = "확인",
        autoDismissOverrideMs: Long = 0L, // 기본: 자동 닫힘 없음
    ) {
        if (!Settings.canDrawOverlays(appCtx)) {
            Log.w(TAG, "No overlay permission.")
            return
        }

        clearBanner()

        modalOnTap = null
        ensureModalView()

        // 회색 톤
        val circle = 0xFFE5E7EB.toInt()
        val iconColor = 0xFF6B7280.toInt()
        val titleColor = 0xFF111111.toInt()

        iconCircleBg?.setColor(circle)
        iconView?.apply {
            // wifi-off 아이콘 리소스가 있으면 여기만 교체하면 됨
            setImageResource(android.R.drawable.stat_notify_error)
            setColorFilter(iconColor)
        }

        titleView?.setTextColor(titleColor)
        titleView?.text = title

        bodyView?.text = message

        // 버튼형 액션
        applyActionAsButton()
        actionView?.text = buttonText

        // 닫기 동작만
        rootView?.setOnClickListener { clearWarning() }
        cardView?.setOnClickListener { /* consume */ }
        actionView?.setOnClickListener { clearWarning() }
        closeView?.setOnClickListener { clearWarning() }

        if (!isModalAdded) {
            try {
                wm.addView(rootView, buildModalLayoutParams())
                isModalAdded = true
            } catch (t: Throwable) {
                Log.e(TAG, "add comm error modal failed: ${t.message}", t)
                isModalAdded = false
            }
        }

        main.removeCallbacks(removeModalRunnable)
        if (autoDismissOverrideMs > 0L) {
            main.postDelayed(removeModalRunnable, autoDismissOverrideMs)
        }
    }

    fun showBanner(
        tone: Tone,
        title: String,
        subtitle: String? = "탭하여 보고서 보기",
        autoDismissMs: Long,
        onTap: (() -> Unit)? = null,
    ) {
        if (!Settings.canDrawOverlays(appCtx)) {
            Log.w(TAG, "No overlay permission.")
            return
        }

        clearWarning()

        bannerOnTap = onTap
        ensureBannerView()

        val p = paletteOf(tone)

        bannerBg?.setColor(p.bannerBg)
        bannerBg?.setStroke(dp(1), alphaColor(p.accent, 0x66))
        bannerBadgeBg?.setColor(p.badgeBg)

        val symbolText = bannerSymbolView
        val symbolIcon = bannerSymbolIconView

        // ✅ FIX: 매 호출마다 심볼 상태를 먼저 리셋 (겹침/잔상 방지)
        symbolText?.visibility = View.GONE
        symbolText?.text = ""
        symbolIcon?.visibility = View.GONE
        symbolIcon?.setImageDrawable(null)

        when (tone) {
            Tone.SAFE -> {
                symbolIcon?.visibility = View.VISIBLE
                symbolIcon?.setImageResource(R.drawable.ic_cc)
            }
            Tone.NOT_AD -> {
                symbolIcon?.visibility = View.VISIBLE
                symbolIcon?.setImageResource(R.drawable.ic_sh)
            }
            Tone.DANGER, Tone.CAUTION -> {
                symbolText?.visibility = View.VISIBLE
                symbolText?.text = p.symbol
                symbolText?.setTextColor(p.bannerText)
            }
        }

        bannerTitleView?.text = title
        bannerTitleView?.setTextColor(p.bannerText)

        bannerSubtitleView?.text = subtitle.orEmpty()
        bannerSubtitleView?.setTextColor(p.bannerSubText)

        val openCb = {
            val cb = bannerOnTap
            clearBanner()
            cb?.invoke()
        }

        bannerView?.setOnClickListener { openCb() }
        bannerSubtitleView?.setOnClickListener { openCb() }
        bannerCloseView?.setOnClickListener { clearBanner() }

        if (!isBannerAdded) {
            try {
                wm.addView(bannerView, buildBannerLayoutParams())
                isBannerAdded = true
            } catch (t: Throwable) {
                Log.e(TAG, "add banner failed: ${t.message}", t)
                isBannerAdded = false
            }
        }

        main.removeCallbacks(removeBannerRunnable)
        if (autoDismissMs > 0L) {
            main.postDelayed(removeBannerRunnable, autoDismissMs)
        }
    }

    // ------------------------------------------------------------
    // View builders
    // ------------------------------------------------------------

    private fun ensureModalView() {
        if (rootView != null) return

        val dm = appCtx.resources.displayMetrics
        val cardWidthPx = minOf((dm.widthPixels * 0.80f).toInt(), dp(340))

        val dimRoot = FrameLayout(appCtx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
        }

        val card = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(18f)
                setColor(0xFFFFFFFF.toInt())
            }

            layoutParams = FrameLayout.LayoutParams(cardWidthPx, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
        }

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
            ).apply { gravity = Gravity.END }
        }
        topRow.addView(x)

        val circleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x33FF3B30.toInt())
        }

        val iconCircle = FrameLayout(appCtx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(18)
            }
            background = circleBg
        }

        val icon = ImageView(appCtx).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(0xFFFF3B30.toInt())
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).apply { gravity = Gravity.CENTER }
        }
        iconCircle.addView(icon)

        val title = TextView(appCtx).apply {
            textSize = 20f // (너가 20으로 줄였다고 했던 값 유지)
            setTextColor(0xFFFF3B30.toInt())
            setPadding(0, dp(12), 0, dp(18))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val body = TextView(appCtx).apply {
            textSize = 13.5f
            setTextColor(0xFF6B7280.toInt())
            gravity = Gravity.CENTER
        }

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
        iconCircleBg = circleBg
        iconView = icon
        closeView = x
        titleView = title
        bodyView = body
        actionView = action
    }

    private fun ensureBannerView() {
        if (bannerView != null) return

        val dm = appCtx.resources.displayMetrics
        bannerWidthPx = minOf((dm.widthPixels * 0.92f).toInt(), dp(380))

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(14f)
            setColor(0xFFEAFBF1.toInt())
        }

        val root = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dp(16), dp(12), dp(12), dp(12))
            isClickable = true
            ViewCompat.setElevation(this, dp(8).toFloat())
        }

        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x3316A34A.toInt())
        }

        val badge = FrameLayout(appCtx).apply {
            background = badgeBg
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dp(12)
            }
        }

        val symbolText = TextView(appCtx).apply {
            visibility = View.GONE
            text = ""
            textSize = 14.5f
            setTextColor(0xFF16A34A.toInt())
            includeFontPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            typeface = Typeface.DEFAULT_BOLD
        }

        val symbolIcon = ImageView(appCtx).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.CENTER
            }
        }

        badge.addView(symbolText)
        badge.addView(symbolIcon)

        val textCol = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(appCtx).apply {
            text = "안전"
            textSize = 15.5f
            setTextColor(0xFF16A34A.toInt())
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(appCtx).apply {
            text = "탭하여 보고서 보기"
            textSize = 12.5f
            setTextColor(0xFF14532D.toInt())
            includeFontPadding = false
            setPadding(0, dp(3), 0, 0)
        }

        textCol.addView(title)
        textCol.addView(subtitle)

        val close = TextView(appCtx).apply {
            text = "✕"
            textSize = 18f
            setTextColor(0xFF6B7280.toInt())
            includeFontPadding = false
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_VERTICAL }
        }

        root.addView(badge)
        root.addView(textCol)
        root.addView(close)

        bannerView = root
        bannerBg = bg
        bannerBadgeBg = badgeBg
        bannerSymbolView = symbolText
        bannerSymbolIconView = symbolIcon
        bannerTitleView = title
        bannerSubtitleView = subtitle
        bannerCloseView = close
    }

    // -----------------------------
    // Action styling helpers
    // -----------------------------

    private fun applyActionAsLink() {
        actionView?.apply {
            background = null
            typeface = Typeface.DEFAULT
            textSize = 12.5f
            setTextColor(0xFF6B7280.toInt())
            gravity = Gravity.CENTER
            // 기존 레이아웃/간격 유지
            setPadding(0, dp(24), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 0 }
        }
    }

    private fun applyActionAsButton() {
        actionView?.apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(12f)
                setColor(0xFF374151.toInt())
            }
            background = bg
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
            // 버튼 내부 패딩
            setPadding(0, dp(12), 0, dp(12))
        }
    }

    // ------------------------------------------------------------
    // WindowManager.LayoutParams
    // ------------------------------------------------------------

    private fun buildModalLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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

    private fun buildBannerLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        val w = if (bannerWidthPx > 0) bannerWidthPx else WindowManager.LayoutParams.WRAP_CONTENT

        return WindowManager.LayoutParams(
            w,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = statusBarHeightPx() + dp(10)
        }
    }
}
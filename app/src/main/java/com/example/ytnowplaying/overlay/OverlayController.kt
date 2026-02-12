package com.example.ytnowplaying.overlay

import android.content.Context
import android.content.Intent

object OverlayController {

    private const val ACTION_SHOW = "com.example.ytnowplaying.overlay.ACTION_SHOW"
    private const val ACTION_HIDE = "com.example.ytnowplaying.overlay.ACTION_HIDE"

    // 기존 호출부 호환: start/stop은 유지하되, stopService를 안 씀(=hide로 변경)
    fun start(ctx: Context) = show(ctx)
    fun stop(ctx: Context) = hide(ctx)

    fun show(ctx: Context) {
        val appCtx = ctx.applicationContext
        appCtx.startService(
            Intent(appCtx, FloatingButtonService::class.java).apply { action = ACTION_SHOW }
        )
    }

    fun hide(ctx: Context) {
        val appCtx = ctx.applicationContext
        appCtx.startService(
            Intent(appCtx, FloatingButtonService::class.java).apply { action = ACTION_HIDE }
        )
    }

    internal fun isShowAction(action: String?): Boolean =
        action == null || action == ACTION_SHOW // null은 과거 startService 호환

    internal fun isHideAction(action: String?): Boolean =
        action == ACTION_HIDE
}

package com.example.ytnowplaying.overlay

import android.content.Context
import android.content.Intent
import com.example.ytnowplaying.overlay.FloatingButtonService

object OverlayController {
    fun start(ctx: Context) {
        val appCtx = ctx.applicationContext
        appCtx.startService(Intent(appCtx, FloatingButtonService::class.java))
    }

    fun stop(ctx: Context) {
        val appCtx = ctx.applicationContext
        appCtx.stopService(Intent(appCtx, FloatingButtonService::class.java))
    }
}

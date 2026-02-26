package com.example.ytnowplaying.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SettingsNavigator {

    fun openNotificationListenerSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openOverlaySettings(ctx: Context) {
        val uri = Uri.parse("package:${ctx.packageName}")
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

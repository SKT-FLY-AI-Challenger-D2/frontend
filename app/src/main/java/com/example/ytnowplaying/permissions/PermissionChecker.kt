package com.example.ytnowplaying.permissions

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionChecker {

    fun hasNotificationListenerAccess(ctx: Context): Boolean {
        val pkgs = NotificationManagerCompat.getEnabledListenerPackages(ctx)
        return pkgs.contains(ctx.packageName)
    }

    fun hasOverlayPermission(ctx: Context): Boolean {
        return Settings.canDrawOverlays(ctx)
    }
}

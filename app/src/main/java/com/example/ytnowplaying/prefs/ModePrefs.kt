package com.example.ytnowplaying.prefs

import android.content.Context

object ModePrefs {
    private const val PREF_NAME = "ytnowplaying_prefs"
    private const val KEY_BG_MODE = "background_mode_enabled"

    fun isBackgroundModeEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BG_MODE, false)

    fun setBackgroundModeEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BG_MODE, enabled)
            .apply()
    }
}

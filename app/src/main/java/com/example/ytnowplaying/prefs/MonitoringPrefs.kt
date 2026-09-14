package com.example.ytnowplaying.prefs

import android.content.Context

/**
 * 수동 모드 돋보기 오버레이 버튼 표시 여부.
 * 배경모드가 켜져 있을 때는 이 값과 무관하게 버튼이 뜨지 않는다(UpdateFloatingButton 참조).
 */
object MonitoringPrefs {
    private const val PREF_NAME = "ytnowplaying_prefs"
    private const val KEY_MANUAL_BUTTON_ENABLED = "manual_button_enabled"

    fun isManualButtonEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MANUAL_BUTTON_ENABLED, true)

    fun setManualButtonEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MANUAL_BUTTON_ENABLED, enabled)
            .apply()
    }
}

package com.example.ytnowplaying.prefs

import android.content.Context

object OnboardingPrefs {
    private const val PREF_NAME = "ytnowplaying_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    fun isDone(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setDone(ctx: Context, done: Boolean) {
        val sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }
}

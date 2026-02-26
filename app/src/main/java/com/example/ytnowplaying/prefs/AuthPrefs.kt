package com.example.ytnowplaying.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREF_NAME = "auth_store"
private const val KEY_LOGGED_IN = "is_logged_in"
private const val KEY_USER_ID = "user_id"
private const val KEY_NAME = "name"
private const val KEY_EMAIL = "user_email"

data class AuthSession(
    val isLoggedIn: Boolean,
    val userId: String? = null,
    val name: String? = null,
    val userEmail: String? = null,
)

object AuthPrefs {

    @Volatile private var initialized = false
    private lateinit var prefs: SharedPreferences

    private val _session = MutableStateFlow(AuthSession(false))
    val session: StateFlow<AuthSession> = _session.asStateFlow()

    fun init(appCtx: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = appCtx.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            load()
            initialized = true
        }
    }

    fun setLoggedIn(userId: String, name: String?, email: String?) {
        ensureInit()
        prefs.edit {
            putBoolean(KEY_LOGGED_IN, true)
            putString(KEY_USER_ID, userId)
            putString(KEY_NAME, name)
            putString(KEY_EMAIL, email)
        }

        _session.value = AuthSession(
            isLoggedIn = true,
            userId = userId,
            name = name,
            userEmail = email
        )
    }

    fun logout() {
        ensureInit()
        prefs.edit { clear() }
        _session.value = AuthSession(false)
    }

    private fun load() {
        val loggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)
        val userId = prefs.getString(KEY_USER_ID, null)
        val name = prefs.getString(KEY_NAME, null)
        val email = prefs.getString(KEY_EMAIL, null)

        _session.value = AuthSession(
            isLoggedIn = loggedIn,
            userId = userId,
            name = name,
            userEmail = email
        )
    }

    private fun ensureInit() {
        if (!initialized) {
            throw IllegalStateException("AuthPrefs is not initialized. Call AuthPrefs.init(context) first.")
        }
    }
}
package com.example.ytnowplaying.render

interface AlertRenderer {
    fun showWarning(text: String, onTap: (() -> Unit)? = null)
    fun clearWarning()
}

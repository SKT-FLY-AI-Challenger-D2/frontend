package com.example.ytnowplaying

import android.app.Application

class RealyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
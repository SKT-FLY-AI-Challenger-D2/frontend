package com.example.ytnowplaying

import android.app.Application
import com.example.ytnowplaying.prefs.AuthPrefs

class RealyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)

        // ✅ 신규: 로그인 상태 저장소 초기화(기존 기능 영향 없음)
        AuthPrefs.init(this)
    }
}
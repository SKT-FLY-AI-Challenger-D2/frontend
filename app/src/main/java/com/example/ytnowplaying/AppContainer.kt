package com.example.ytnowplaying

import android.content.Context
import com.example.ytnowplaying.data.report.InMemoryReportRepository
import com.example.ytnowplaying.data.report.ReportRepository

object AppContainer {

    @Volatile
    private var _repo: ReportRepository? = null

    val reportRepository: ReportRepository
        get() = _repo ?: error("AppContainer is not initialized. Call AppContainer.init(context) first.")

    fun init(appCtx: Context) {
        if (_repo != null) return
        synchronized(this) {
            if (_repo == null) {
                _repo = InMemoryReportRepository(appCtx.applicationContext)
            }
        }
    }
}
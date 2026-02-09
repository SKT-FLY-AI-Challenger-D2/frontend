package com.example.ytnowplaying

import com.example.ytnowplaying.data.report.InMemoryReportRepository
import com.example.ytnowplaying.data.report.ReportRepository

object AppContainer {
    val reportRepository: ReportRepository = InMemoryReportRepository()
}

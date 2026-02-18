package com.example.ytnowplaying.data.report

import kotlinx.coroutines.flow.StateFlow

interface ReportRepository {
    suspend fun getReport(id: String): Report?
    suspend fun saveReport(report: Report)
    suspend fun listReports(): List<Report>

    // ✅ UI가 즉시 갱신되도록: 보고서 목록을 관찰 가능하게 제공
    fun observeReports(): StateFlow<List<Report>>
}

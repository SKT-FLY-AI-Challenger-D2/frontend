package com.example.ytnowplaying.data.report

import kotlinx.coroutines.flow.StateFlow

interface ReportRepository {
    suspend fun getReport(id: String): Report?
    suspend fun saveReport(report: Report)
    suspend fun listReports(): List<Report>
    fun observeReports(): StateFlow<List<Report>>

    // ✅ 추가
    suspend fun clearAllReports()
}
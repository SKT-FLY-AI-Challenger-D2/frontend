package com.example.ytnowplaying.data.report

interface ReportRepository {
    suspend fun listReports(): List<Report>
    suspend fun getReport(id: String): Report?
}

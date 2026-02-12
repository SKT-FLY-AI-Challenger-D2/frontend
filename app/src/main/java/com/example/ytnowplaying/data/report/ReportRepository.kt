package com.example.ytnowplaying.data.report

interface ReportRepository {
    suspend fun listReports(): List<Report>
    suspend fun getReport(id: String): Report?

    /**
     * In-memory 구현 기준으로는 upsert 동작.
     * (id가 같으면 교체, 없으면 추가)
     */
    suspend fun saveReport(report: Report)
}

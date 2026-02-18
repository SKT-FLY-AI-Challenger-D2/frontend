package com.example.ytnowplaying.data.report

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryReportRepository : ReportRepository {

    private val mutex = Mutex()

    // id -> Report
    private val map = LinkedHashMap<String, Report>()

    // ✅ UI에서 바로 구독할 상태
    private val reportsState = MutableStateFlow<List<Report>>(emptyList())

    override fun observeReports(): StateFlow<List<Report>> = reportsState.asStateFlow()

    override suspend fun getReport(id: String): Report? =
        mutex.withLock { map[id] }

    override suspend fun saveReport(report: Report) {
        mutex.withLock {
            map[report.id] = report

            // 최신순 정렬(현재 UI/의도와 동일)
            reportsState.value = map.values
                .sortedByDescending { it.detectedAtEpochMs }
        }
    }

    override suspend fun listReports(): List<Report> =
        reportsState.value
}

package com.example.ytnowplaying.data.report

class InMemoryReportRepository : ReportRepository {

    private val seed = listOf(
        Report(
            id = "demo",
            title = "데모: 의심 영상 감지",
            channel = "데모 채널",
            detectedAtEpochMs = System.currentTimeMillis(),
            summary = "PDF 근거 없음: 임시 더미 요약",
            severity = Severity.HIGH,
            confidencePercent = 87,
        ),
        Report(
            id = "r2",
            title = "데모: 투자 사기형 콘텐츠",
            channel = "Sample Channel",
            detectedAtEpochMs = System.currentTimeMillis() - 86_400_000L,
            summary = "PDF 근거 없음: 임시 더미 요약",
            severity = Severity.MEDIUM,
            confidencePercent = 72,
        ),
        Report(
            id = "r3",
            title = "데모: 건강식품 과장 광고",
            channel = "Sample Channel 2",
            detectedAtEpochMs = System.currentTimeMillis() - 2 * 86_400_000L,
            summary = "PDF 근거 없음: 임시 더미 요약",
            severity = Severity.LOW,
            confidencePercent = 55,
        ),
    )

    override suspend fun listReports(): List<Report> = seed

    override suspend fun getReport(id: String): Report? = seed.firstOrNull { it.id == id }
}

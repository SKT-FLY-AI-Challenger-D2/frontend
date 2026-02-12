package com.example.ytnowplaying.data.report

import java.util.concurrent.CopyOnWriteArrayList

class InMemoryReportRepository : ReportRepository {

    private val items = CopyOnWriteArrayList<Report>()

    init {
        // Demo seed
        val now = System.currentTimeMillis()
        items.add(
            Report(
                id = "demo-danger",
                detectedAtEpochMs = now - 10_000L,
                title = "의심스러운 정치인 인터뷰 영상",
                channel = "OOO 채널",
                durationSec = 123L,
                scorePercent = 87,
                severity = Severity.DANGER,
                dangerEvidence = listOf(
                    "발화의 맥락과 화면 자막이 불일치함",
                    "원본 출처 링크가 제시되지 않음",
                    "자극적인 문구로 외부 링크 유도를 시도함"
                ),
                summary = "이 영상은 조작되었을 가능성이 있습니다.",
                detail = "여러 정황상 원본 대비 내용이 편집되었을 가능성이 높습니다. ..."
            )
        )
        items.add(
            Report(
                id = "demo-caution",
                detectedAtEpochMs = now - 60_000L,
                title = "뉴스 클립 영상",
                channel = "NEWS",
                durationSec = 52L,
                scorePercent = 52,
                severity = Severity.CAUTION,
                dangerEvidence = listOf(
                    "맥락이 일부 생략되어 오해 여지가 있음"
                ),
                summary = "자극적인 편집으로 오해를 유도할 수 있습니다.",
                detail = "전체 방송분과 비교하면 일부 발언이 잘려 전달될 수 있습니다. ..."
            )
        )
        items.add(
            Report(
                id = "demo-safe",
                detectedAtEpochMs = now - 3 * 60_000L,
                title = "일반 유튜브 영상",
                channel = "일상채널",
                durationSec = 90L,
                scorePercent = 15,
                severity = Severity.SAFE,
                dangerEvidence = emptyList(),
                summary = "특이한 위험 요소가 뚜렷하지 않습니다.",
                detail = "현재 제공된 정보 기준으로는 위험 요소가 낮습니다. ..."
            )
        )
    }

    override suspend fun listReports(): List<Report> {
        // 최신 순
        return items.sortedByDescending { it.detectedAtEpochMs }
    }

    override suspend fun getReport(id: String): Report? {
        return items.firstOrNull { it.id == id }
    }

    override suspend fun saveReport(report: Report) {
        // upsert
        val idx = items.indexOfFirst { it.id == report.id }
        if (idx >= 0) items[idx] = report else items.add(report)
    }
}

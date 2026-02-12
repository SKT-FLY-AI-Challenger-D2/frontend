package com.example.ytnowplaying.data.report

/**
 * Backend 분석 결과를 앱 내부에서 저장/표시하기 위한 도메인 모델.
 */
data class Report(
    val id: String,
    val detectedAtEpochMs: Long,

    val title: String,
    val channel: String? = null,
    val durationSec: Long? = null,

    /** 0..100 */
    val scorePercent: Int,
    val severity: Severity,

    /**
     * 위험/주의에서만 의미가 있다.
     * 안전일 때는 UI에서 출력하지 않는다.
     */
    val dangerEvidence: List<String> = emptyList(),

    /** 선제 요약 문구 (short_report) */
    val summary: String,

    /** 구체적 분석 결과 (analysis_report) */
    val detail: String,
)

enum class Severity {
    DANGER,
    CAUTION,
    SAFE,
}

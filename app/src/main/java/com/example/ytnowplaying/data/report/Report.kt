package com.example.ytnowplaying.data.report

data class Report(
    val id: String,
    val title: String,
    val channel: String,
    val detectedAtEpochMs: Long,
    val summary: String,
    val severity: Severity,
    val confidencePercent: Int,
)

enum class Severity {
    LOW, MEDIUM, HIGH
}

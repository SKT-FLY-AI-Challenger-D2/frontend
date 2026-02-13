// ReportScreen.kt (전체 교체)

package com.example.ytnowplaying.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.ReportRepository
import com.example.ytnowplaying.data.report.Severity

private const val YOUTUBE_PKG = "com.google.android.youtube"

@Composable
fun ReportScreen(
    repo: ReportRepository,
    reportId: String,
    launchedFromOverlay: Boolean,
    alertText: String?,
    onOpenTutorial: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var report by remember { mutableStateOf<Report?>(null) }

    LaunchedEffect(reportId) {
        report = repo.getReport(reportId)
        android.util.Log.d("REALY_AI", "[LOAD] reportId=$reportId found=${report != null}")
    }

    BackHandler {
        if (launchedFromOverlay) {
            openYouTube(ctx)
            (ctx as? Activity)?.finish()
        } else {
            onBack()
        }
    }

    val fallback = remember(reportId, alertText) {
        if (alertText.isNullOrBlank()) null
        else Report(
            id = reportId,
            detectedAtEpochMs = System.currentTimeMillis(),
            title = "분석 결과",
            channel = null,
            durationSec = null,
            scorePercent = 0,
            severity = Severity.CAUTION,
            dangerEvidence = emptyList(),
            summary = alertText.trim(),
            detail = alertText.trim(),
        )
    }

    val r = report ?: fallback

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
    ) {
        ReportTopBar(
            onBack = {
                if (launchedFromOverlay) {
                    openYouTube(ctx)
                    (ctx as? Activity)?.finish()
                } else {
                    onBack()
                }
            }
        )

        if (r == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "보고서를 불러오지 못했습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ReportHeaderCard(r) }

            if (r.severity != Severity.SAFE && r.dangerEvidence.isNotEmpty()) {
                item { EvidenceCard(r.severity, r.dangerEvidence) }
            }

            item { SectionCard(r.severity, kind = SectionKind.SUMMARY, title = "요약", body = r.summary) }
            item { SectionCard(r.severity, kind = SectionKind.DETAIL, title = "상세 분석", body = r.detail) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButtonSolidBlue(
                        text = "관련 영상 덜 보는 법",
                        onClick = onOpenTutorial
                    )
                    SecondaryButtonBlack(
                        text = "유튜브로 돌아가기",
                        onClick = {
                            openYouTube(ctx)
                            (ctx as? Activity)?.finish()
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

/* ---------------- TopBar (SettingsScreen과 동일 규격) ---------------- */

// ✅ ReportScreen.kt 안에서 ReportTopBar()를 아래 코드로 "그대로 교체"해.
// (SettingsTopBar 규격: height=64, horizontal padding=16, back=← 32sp/semibold,
//  back modifier: clickable + padding(end=14, bottom=8), title=22sp/semibold)

@Composable
private fun ReportTopBar(
    onBack: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)                 // ✅ Settings와 동일
                .padding(horizontal = 16.dp),  // ✅ Settings와 동일
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",                    // ✅ Settings와 동일
                fontSize = 32.sp,              // ✅ Settings와 동일
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 14.dp, top = 0.dp, bottom = 8.dp) // ✅ Settings와 동일
            )

            Text(
                text = "분석 보고서",           // ✅ 제목만 Report로
                fontSize = 22.sp,              // ✅ Settings와 동일
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
        }
    }
}



/* ---------------- Severity theme ---------------- */

private enum class SectionKind { SUMMARY, DETAIL }

private data class SeverityTheme(
    val gradient: Brush,
    val accent: Color,
    val summaryBg: Color,
)

@Composable
private fun rememberSeverityTheme(sev: Severity): SeverityTheme {
    return when (sev) {
        Severity.DANGER -> SeverityTheme(
            gradient = Brush.horizontalGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF6B6B))),
            accent = Color(0xFFFF3B30),
            summaryBg = Color(0xFFFFEEF0)
        )
        Severity.CAUTION -> SeverityTheme(
            gradient = Brush.horizontalGradient(listOf(Color(0xFFFF8A00), Color(0xFFFFC107))),
            accent = Color(0xFFFF8A00),
            summaryBg = Color(0xFFFFF3E6)
        )
        Severity.SAFE -> SeverityTheme(
            gradient = Brush.horizontalGradient(listOf(Color(0xFF00C853), Color(0xFF00E676))),
            accent = Color(0xFF00C853),
            summaryBg = Color(0xFFEAFBF1)
        )
    }
}

@Composable
private fun CircleBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/* ---------------- Header Card ---------------- */

@Composable
private fun ReportHeaderCard(r: Report) {
    val theme = rememberSeverityTheme(r.severity)

    val riskLabel = when (r.severity) {
        Severity.DANGER -> "위험"
        Severity.CAUTION -> "주의"
        Severity.SAFE -> "안전"
    }

    val scoreText = if (r.scorePercent in 1..100) "${r.scorePercent}%" else "-"

    val channelText = r.channel?.trim().orEmpty().ifBlank { "채널 정보 없음" }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .background(theme.gradient)
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = r.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 28.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = channelText,
                    color = Color(0xCCFFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = riskLabel,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "위험도",
                            color = Color(0xCCFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = scoreText,
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- Evidence Card ---------------- */

@Composable
private fun EvidenceCard(sev: Severity, dangerEvidence: List<String>) {
    val theme = rememberSeverityTheme(sev)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ✅ "느낌표 세모" = ⚠
                Text(
                    text = "⚠",
                    color = theme.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(10.dp))

                // ✅ 타이틀 크기/색
                Text(
                    text = "위험 요소",
                    color = theme.accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // ✅ 점(•) 대신 "!" / ✅ 줄 간격 증가
            dangerEvidence.forEachIndexed { idx, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "!",
                        color = theme.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = line,
                        color = Color(0xFF111111),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (idx != dangerEvidence.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/* ---------------- Section Card (Summary/Detail) ---------------- */

@Composable
private fun SectionCard(
    sev: Severity,
    kind: SectionKind,
    title: String,
    body: String
) {
    val theme = rememberSeverityTheme(sev)

    val container = when (kind) {
        SectionKind.SUMMARY -> theme.summaryBg
        SectionKind.DETAIL -> Color.White
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (kind) {
                    SectionKind.SUMMARY -> {
                        // ✅ 요약: i 아이콘
                        CircleBadge(text = "i", color = theme.accent)
                        Spacer(Modifier.width(10.dp))
                    }
                    SectionKind.DETAIL -> {
                        // ✅ 상세 분석: 왼쪽 아무것도 없음
                    }
                }

                Text(
                    text = title,
                    color = theme.accent,
                    fontSize = 17.sp,            // ✅ 타이틀 크기 업
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = body,
                color = Color(0xFF111111),
                fontSize = 15.sp,              // ✅ 요약 내용 크기 업(상세도 동일)
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/* ---------------- Buttons ---------------- */

@Composable
private fun PrimaryButtonSolidBlue(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF2563EB))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SecondaryButtonBlack(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF111827))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun openYouTube(ctx: Context) {
    val pm = ctx.packageManager
    val i = pm.getLaunchIntentForPackage(YOUTUBE_PKG)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}

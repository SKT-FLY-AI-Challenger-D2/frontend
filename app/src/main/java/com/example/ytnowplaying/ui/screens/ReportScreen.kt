package com.example.ytnowplaying.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
        // 프로세스가 죽은 뒤 오버레이에서 앱이 다시 뜨면 in-memory repo가 비어 있을 수 있음.
        // 이 경우 최소한 텍스트는 보여준다.
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
            item {
                RiskHeaderCard(r)
            }

            if (r.severity != Severity.SAFE && r.dangerEvidence.isNotEmpty()) {
                item {
                    EvidenceCard(r.dangerEvidence)
                }
            }

            item {
                SimpleSectionCard(title = "요약", body = r.summary)
            }

            item {
                SimpleSectionCard(title = "상세 분석", body = r.detail)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "관련 영상 덜 보는 법",
                        onClick = onOpenTutorial
                    )

                    SecondaryButton(
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
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 22.sp,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable { onBack() }
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = "분석 보고서",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF111111)
            )
        }
    }
}

@Composable
private fun RiskHeaderCard(r: Report) {
    val (bg, fg, label) = when (r.severity) {
        Severity.DANGER -> Triple(Color(0xFFFFE4E6), Color(0xFFDC2626), "위험")
        Severity.CAUTION -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), "주의")
        Severity.SAFE -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), "안전")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = r.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp
                ),
                color = Color(0xFF111111),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = fg,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = if (r.scorePercent <= 0) "-" else "${r.scorePercent}%",
                    color = fg,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )

                Spacer(Modifier.weight(1f))

                // (선택) 채널 표시
                val ch = r.channel?.trim().orEmpty()
                if (ch.isNotBlank()) {
                    Text(
                        text = ch,
                        color = Color(0xFF374151),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceCard(dangerEvidence: List<String>) {
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
            Text(
                text = "위험 요소",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF111111)
            )

            Spacer(Modifier.height(10.dp))

            dangerEvidence.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = Color(0xFFDC2626),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF374151),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SimpleSectionCard(
    title: String,
    body: String,
) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF111111)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF374151),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF4F8DF7), Color(0xFF8A2BE2))
                )
            )
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
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
) {
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

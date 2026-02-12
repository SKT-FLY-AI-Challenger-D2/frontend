package com.example.ytnowplaying.ui.screens

import android.provider.Settings
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.AppContainer
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.Severity
import com.example.ytnowplaying.overlay.OverlayController
import com.example.ytnowplaying.prefs.ModePrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    onOpenHistory: () -> Unit,   // 유지(지금 UI에선 미사용)
    onOpenReport: (String) -> Unit,
    onOpenSettings: () -> Unit,  // ✅ 추가
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val repo = AppContainer.reportRepository

    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }

    // Main 진입 시: 버튼 모드일 때만 플로팅 버튼 서비스 시작(권한 있을 때)
    LaunchedEffect(Unit) {

        reports = repo.listReports()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
    ) {
        TopBar(onOpenSettings = onOpenSettings)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MainHeroCard(
                    onAnalyzeClick = {
                        // 수동 모드: 플로팅 버튼을 ‘띄우는’ 게 아니라
                        // 유튜브가 켜져있을 때 NLS가 알아서 띄우게 둔다.
                        // 여기서는 사용자에게 안내만.
                        if (!ModePrefs.isBackgroundModeEnabled(ctx)) {
                            android.widget.Toast
                                .makeText(ctx, "유튜브에서 영상을 재생 중일 때 오른쪽 버튼을 눌러 분석하세요.", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                )
            }

            item { AnalysisHeader(count = reports.size) }

            items(reports, key = { it.id }) { r ->
                ReportRow(
                    report = r,
                    onClick = { onOpenReport(r.id) }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    onOpenSettings: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REALY.AI",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF111111)
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "⚙️",
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable { onOpenSettings() }
                    .padding(6.dp)
            )
        }
    }
}

// 이하(카드/리스트/유틸) 코드는 너가 붙인 그대로 유지


@Composable
private fun MainHeroCard(
    onAnalyzeClick: () -> Unit
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "영상이 의심되면\n버튼을 눌러주세요!",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp
                ),
                color = Color(0xFF111111)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "실시간으로 영상을 분석합니다",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280)
            )

            Spacer(Modifier.height(14.dp))

            GradientPillButton(
                text = "🔍  영상 분석하기",
                onClick = onAnalyzeClick
            )
        }
    }
}

@Composable
private fun GradientPillButton(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF4F8DF7), Color(0xFF8A2BE2))
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun AnalysisHeader(count: Int) {
    Column {
        Text(
            text = "분석 기록",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF111111)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildAnnotatedString {
                append("총 ")
                withStyle(SpanStyle(color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)) {
                    append("${count}개")
                }
                append("의 분석 기록")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280)
        )
    }
}

@Composable
private fun ReportRow(
    report: Report,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeverityIcon(severity = report.severity)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatKoreanDateTime(report.detectedAtEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = report.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF111111),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                SeverityChip(report.severity)
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = "보고서 보기 →",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun SeverityIcon(severity: Severity) {
    val (bg, fg, symbol) = when (severity) {
        Severity.DANGER -> Triple(Color(0xFFFFE4E6), Color(0xFFDC2626), "⚠")
        Severity.CAUTION -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), "!")
        Severity.SAFE -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), "✓")
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeverityChip(severity: Severity) {
    val (label, bg, fg) = when (severity) {
        Severity.DANGER -> Triple("위험도: 위험", Color(0xFFFFE4E6), Color(0xFFDC2626))
        Severity.CAUTION -> Triple("위험도: 주의", Color(0xFFFFEDD5), Color(0xFFEA580C))
        Severity.SAFE -> Triple("위험도: 안전", Color(0xFFDCFCE7), Color(0xFF16A34A))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun formatKoreanDateTime(epochMs: Long): String {
    // 예: 2026. 2. 9. 오후 3:24
    val sdf = SimpleDateFormat("yyyy. M. d. a h:mm", Locale.KOREA)
    return sdf.format(Date(epochMs))
}

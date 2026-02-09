package com.example.ytnowplaying.ui.screens

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ytnowplaying.AppContainer
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.overlay.OverlayController

@Composable
fun MainScreen(
    onOpenHistory: () -> Unit,
    onOpenReport: (String) -> Unit,
) {
    val ctx = LocalContext.current.applicationContext

    // ✅ Main 진입 시: 플로팅 버튼 시작은 “한 곳(OverlayController)”에서만
    LaunchedEffect(Unit) {
        if (Settings.canDrawOverlays(ctx)) {
            OverlayController.start(ctx)
        }
    }

    val repo = AppContainer.reportRepository
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }

    LaunchedEffect(Unit) {
        reports = repo.listReports()
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("영상이 의심되면 버튼을 눌러주세요!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("오른쪽 중간에 플로팅 버튼이 표시됩니다.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text("PDF 근거 없음: 임시 처리 (플로팅 버튼 자동 시작 안내)", style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("분석 기록")
        }

        Spacer(Modifier.height(16.dp))
        Text("총 ${reports.size}개의 분석 기록", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        reports.take(4).forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(r.title, style = MaterialTheme.typography.bodyLarge)
                Text("채널: ${r.channel}", style = MaterialTheme.typography.bodySmall)
                Text("위험도: ${r.severity}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onOpenReport(r.id) }, modifier = Modifier.height(40.dp)) {
                    Text("보고서 보기")
                }
            }
        }
    }
}

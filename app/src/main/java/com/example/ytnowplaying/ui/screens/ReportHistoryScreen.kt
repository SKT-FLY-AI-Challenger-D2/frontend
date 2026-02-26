package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.ReportRepository

@Composable
fun ReportHistoryScreen(
    repo: ReportRepository,
    onOpenReport: (String) -> Unit,
) {
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }

    LaunchedEffect(Unit) {
        reports = repo.listReports()
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("역대 보고서 목록", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("PDF 근거 없음: 임시 처리 (목록 헤더 문구)", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))

        reports.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(r.title, style = MaterialTheme.typography.bodyLarge)
                Text("채널: ${r.channel}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onOpenReport(r.id) }, modifier = Modifier.height(40.dp)) {
                    Text("보고서 보기")
                }
            }
        }
    }
}

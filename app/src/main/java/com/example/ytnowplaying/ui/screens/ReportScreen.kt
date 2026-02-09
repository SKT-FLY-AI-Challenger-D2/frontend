package com.example.ytnowplaying.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ytnowplaying.data.report.Report
import com.example.ytnowplaying.data.report.ReportRepository

private const val YOUTUBE_PKG = "com.google.android.youtube"

@Composable
fun ReportScreen(
    repo: ReportRepository,
    reportId: String,
    launchedFromOverlay: Boolean,
    alertText: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf<Report?>(null) }

    LaunchedEffect(reportId) {
        report = repo.getReport(reportId)
    }

    // 요구사항: “보고서 화면에서 뒤로가기를 누르면 다시 유튜브로”
    BackHandler {
        if (launchedFromOverlay) {
            (ctx as? Activity)?.finish()
        } else {
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("보고서(준비중)", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(12.dp))

        // ✅ 임시 표시
        if (!alertText.isNullOrBlank()) {
            Text("알림 내용", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(alertText, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
        } else {
            Text("알림 내용이 없습니다(임시).", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                // 유튜브 복귀: overlay로 열린 경우 finish만으로도 대부분 유튜브로 돌아감.
                // 앱 단독 실행 상태에서도 동작하도록 유튜브 런치 인텐트도 시도.
                val pm = ctx.packageManager
                val yt = pm.getLaunchIntentForPackage(YOUTUBE_PKG)
                if (yt != null) {
                    yt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(yt)
                }
                (ctx as? Activity)?.finish()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("YouTube로 돌아가기")
        }
    }
}

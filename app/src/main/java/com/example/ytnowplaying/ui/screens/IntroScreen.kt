package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IntroScreen(
    onNext: () -> Unit
) {
    // PDF 근거: “서비스 소개(신뢰도 목적) 페이지 1장” + “다음”
    // PDF의 세부 문구를 텍스트 추출로 확정하기 어려워서(폰트 인코딩) 임시 처리 라벨을 명시.
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("XXX AI와 함께 안전한 영상 시청", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Text("PDF 근거 없음: 임시 처리", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))

        Text("• (임시) 의심되는 영상 경고", style = MaterialTheme.typography.bodyMedium)
        Text("• (임시) 간단한 보고서 화면 제공(현재는 준비만)", style = MaterialTheme.typography.bodyMedium)
        Text("• (임시) 오버레이로 유튜브 시청 중에도 확인", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("다음")
        }
    }
}

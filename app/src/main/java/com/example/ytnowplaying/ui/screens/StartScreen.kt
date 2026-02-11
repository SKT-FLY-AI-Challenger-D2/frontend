package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun StartScreen(
    onStart: () -> Unit
) {
    // 스크린샷 기준: 위(블루) -> 아래(퍼플) 그라데이션
    val topBlue = Color(0xFF3872FE)
    val bottomPurple = Color(0xFF9216FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topBlue, bottomPurple)
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        // 중앙 컨텐츠
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 40.dp), // 버튼이 하단에 있어서 시각적 중심 보정
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✦",
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "REALLY AI",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "영상의 진위를 검증하는\n스마트 AI 도우미",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 18.sp,
                lineHeight = 26.sp,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // 하단 버튼(필 형태)
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .width(220.dp)
                .height(56.dp)
        ) {
            Text(
                text = "시작하기",
                color = Color(0xFF7A2AFB), // 보라 톤 텍스트
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun SplashScreen() {
    val topBlue = Color(0xFF3872FE)
    val bottomPurple = Color(0xFF9216FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(topBlue, bottomPurple)))
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 24.dp),
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
                    text = "REALY.AI",
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

            Spacer(Modifier.height(28.dp))

            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

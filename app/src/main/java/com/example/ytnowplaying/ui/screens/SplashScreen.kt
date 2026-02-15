package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.R

@Composable
fun SplashScreen() {
    val primaryBlue = Color(0xFF2563EB)
    val subtitleGray = Color(0xFF6B7280)
    val brandBlue = Color(0xFF2563EB)

    // ✅ StartScreen과 동일하게 맞춤
    val logoResId = R.drawable.realy_logo

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "REALY.AI Logo",
                    modifier = Modifier.size(56.dp),   // ✅ 로고 크기 (44 -> 56)
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "REALY.AI",
                    fontSize = 40.sp,                 // ✅ 글씨 크기 (26 -> 32)
                    fontWeight = FontWeight.SemiBold,
                    color = brandBlue                 // ✅ 파란색으로
                )
            }


            Spacer(Modifier.height(18.dp))

            Text(
                text = "영상의 진위를 검증하는\n스마트 AI 도우미",
                color = subtitleGray,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(22.dp))

            CircularProgressIndicator(
                color = primaryBlue,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
fun StartScreen(
    onStart: () -> Unit
) {
    val primaryBlue = Color(0xFF2563EB)
    val subtitleGray = Color(0xFF6B7280)
    val brandBlue = Color(0xFF2563EB)

    // ✅ 여기만 바꾸면 됨:
    // - app icon foreground를 로고처럼 쓰려면 R.mipmap.ic_launcher_foreground
    // - drawable에 별도 로고(realy_logo.png) 넣었으면 R.drawable.realy_logo
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
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✅ 로고: 아이콘 단독 + 텍스트
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
                Spacer(Modifier.width(0.dp))
                Text(
                    text = "REALY.AI",
                    fontSize = 40.sp,                 // ✅ 글씨 크기 (26 -> 32)
                    fontWeight = FontWeight.SemiBold,
                    color = brandBlue                 // ✅ 파란색으로
                )
            }


            Spacer(Modifier.height(18.dp))

            Text(
                text = "광고의 위험성을 분석하는\n스마트 AI 도우미",
                color = subtitleGray,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .width(220.dp)
                .height(54.dp)
        ) {
            Text(
                text = "시작하기",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

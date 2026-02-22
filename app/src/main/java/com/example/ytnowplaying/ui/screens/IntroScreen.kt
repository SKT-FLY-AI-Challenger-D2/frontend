package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntroScreen(
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF6FAFF)
                    )
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 텍스트 블록 위치(원하면 숫자만 더 키우면 더 내려감)
            Spacer(Modifier.height(120.dp))

            Text(
                text = "REALY.AI와 함께\n안전한 영상 시청",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
                lineHeight = 32.sp
            )

            Spacer(Modifier.height(36.dp))

            FeatureRow(
                iconText = "👁",
                iconBg = Color(0xFFE8F1FF),
                title = "실시간 영상 분석",
                desc = "AI가 영상을 실시간으로 분석하여 광고 영상의 위험도를 판단합니다."
            )
            Spacer(Modifier.height(22.dp))
            FeatureRow(
                iconText = "🛡",
                iconBg = Color(0xFFF2E9FF),
                title = "신뢰할 수 있는 보호",
                desc = "딥페이크 탐지 기술을 활용하여 AI 악용 영상을 걸러냅니다"
            )
            Spacer(Modifier.height(22.dp))
            FeatureRow(
                iconText = "✓",
                iconBg = Color(0xFFE9F9EF),
                title = "상세한 분석 보고서",
                desc = "감지한 허위/사기 광고에 대한 자세한 분석 결과를 제공합니다"
            )

            Spacer(Modifier.weight(1f))

            GradientButton(
                text = "다음",
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            )

            Spacer(Modifier.height(14.dp))

            PageDots(
                total = 3,
                activeIndex = 2
            )
        }
    }
}

@Composable
private fun FeatureRow(
    iconText: String,
    iconBg: Color,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconText,
                fontSize = 20.sp,
                color = Color(0xFF111111)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = desc,
                fontSize = 15.sp,
                color = Color(0xFF7A7A7A),
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF2F6BFF),
            Color(0xFF8A2CFF)
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PageDots(
    total: Int,
    activeIndex: Int
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { idx ->
            val color = if (idx == activeIndex) Color(0xFF2F6BFF) else Color(0xFFD7D7D7)
            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.permissions.PermissionChecker
import com.example.ytnowplaying.permissions.SettingsNavigator
import com.example.ytnowplaying.ui.util.OnResumeEffect

@Composable
fun PermissionStep2Screen(
    onGranted: () -> Unit
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(PermissionChecker.hasOverlayPermission(ctx)) }

    OnResumeEffect {
        granted = PermissionChecker.hasOverlayPermission(ctx)
        if (granted) onGranted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
    ) {
        // ✅ 중앙 컨텐츠 (Step1과 동일 톤)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✅ 아이콘 크게
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .background(Color(0xFFF3E8FF), CircleShape), // Step2는 보라 계열로
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🧩", // 문자 아이콘(원하면 "🪟" 같은 걸로 바꿔도 됨)
                    fontSize = 40.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            // ✅ 타이틀 크게
            Text(
                text = "다음 권한을\n설정해주세요",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                lineHeight = 38.sp
            )

            Spacer(Modifier.height(14.dp))

            // ✅ 설명 크게
            Text(
                text = "경고 표시를 위해\n오버레이 권한이 필요합니다",
                fontSize = 17.sp,
                color = Color(0xFF6B7280),
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(26.dp))

            // ✅ 버튼 크기/톤 Step1과 동일 구조
            Button(
                onClick = { SettingsNavigator.openOverlaySettings(ctx) },
                enabled = !granted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6D28D9),      // 보라 버튼
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFC4B5FD),
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .width(240.dp)
                    .height(54.dp)
            ) {
                Text(
                    text = "⚙ 설정하러 가기",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // (선택) granted 안내 문구를 화면에 굳이 띄우고 싶으면 아래 주석 해제
            /*
            Spacer(Modifier.height(12.dp))
            if (granted) {
                Text(
                    text = "권한이 설정되었습니다. 다음 단계로 이동합니다.",
                    fontSize = 14.sp,
                    color = Color(0xFF10B981)
                )
            }
            */
        }

        // ✅ 페이지 인디케이터: 하단 고정 (Step2는 가운데 점 활성화)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Dot(active = false)
            Dot(active = true)
            Dot(active = false)
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(if (active) 8.dp else 7.dp)
            .background(
                color = if (active) Color(0xFF6B7280) else Color(0xFFD1D5DB),
                shape = CircleShape
            )
    )
}

package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
fun PermissionStep1Screen(
    onGranted: () -> Unit
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(PermissionChecker.hasNotificationListenerAccess(ctx)) }

    OnResumeEffect {
        granted = PermissionChecker.hasNotificationListenerAccess(ctx)
        if (granted) onGranted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
    ) {
        // ✅ 중앙 컨텐츠
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp), // 인디케이터 하단 고정 대비 여백
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✅ 아이콘 더 크게
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .background(Color(0xFFE8F1FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡",
                    fontSize = 40.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            // ✅ 타이틀 더 크게
            Text(
                text = "먼저 권한을\n설정해주세요",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                lineHeight = 38.sp
            )

            Spacer(Modifier.height(14.dp))

            // ✅ 설명 더 크게
            Text(
                text = "영상 분석을 위해\n접근성 권한이 필요합니다",
                fontSize = 17.sp,
                color = Color(0xFF6B7280),
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(26.dp))

            // ✅ 버튼 크기 약간 키움
            Button(
                onClick = { SettingsNavigator.openNotificationListenerSettings(ctx) },
                enabled = !granted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF93C5FD),
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
        }

        // ✅ 페이지 인디케이터: 화면 하단 고정
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Dot(active = true)
            Dot(active = false)
            Dot(active = false)
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(if (active) 8.dp else 7.dp) // 활성 dot 약간 큼
            .background(
                color = if (active) Color(0xFF6B7280) else Color(0xFFD1D5DB),
                shape = CircleShape
            )
    )
}

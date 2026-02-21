// SettingsScreen.kt (전체 교체)
package com.example.ytnowplaying.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.AppContainer
import com.example.ytnowplaying.R
import com.example.ytnowplaying.overlay.OverlayController
import com.example.ytnowplaying.permissions.PermissionChecker
import com.example.ytnowplaying.prefs.ModePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var bgEnabled by remember { mutableStateOf(ModePrefs.isBackgroundModeEnabled(ctx)) }

    // 권한 상태(표시용)
    val hasNls by remember { mutableStateOf(PermissionChecker.hasNotificationListenerAccess(ctx)) }
    val hasOverlay by remember { mutableStateOf(PermissionChecker.hasOverlayPermission(ctx)) }

    // ✅ 분석 기록 상태 구독 (0개 여부/즉시 갱신용)
    val repo = AppContainer.reportRepository
    val reports by repo.observeReports().collectAsState()
    val isEmpty = reports.isEmpty()

    // ✅ 0개일 때 터치 피드백(색상 잠깐 진해졌다가 복귀)
    var emptyPulse by remember { mutableStateOf(false) }
    val clearCardBg by animateColorAsState(
        targetValue = when {
            isEmpty && emptyPulse -> Color(0xFFE5E7EB) // 눌렀을 때 피드백
            isEmpty -> Color(0xFFF3F4F6)              // 0개면 비활성 느낌
            else -> Color.White
        },
        label = "clearCardBg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        SettingsTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // 상단 카드 (토글)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "✦", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "백그라운드 모드",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111111)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "유튜브를 시청하는 동안 자동으로 영상\n을 분석합니다.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Switch(
                        checked = bgEnabled,
                        modifier = Modifier.scale(1.10f),
                        onCheckedChange = { newValue ->
                            bgEnabled = newValue
                            ModePrefs.setBackgroundModeEnabled(ctx, newValue)

                            // ON: 플로팅 버튼 숨김 / OFF: 버튼 모드 복귀
                            if (newValue) {
                                OverlayController.stop(ctx)
                            } else {
                                // (현 구조 유지)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 상태 안내 박스
            val boxBg = if (bgEnabled) Color(0xFFEAF2FF) else Color(0xFFF3F4F6)
            val title = if (bgEnabled) "백그라운드 모드 활성화됨" else "백그라운드 모드 비활성화됨"
            val body = if (bgEnabled) {
                "유튜브를 시청하는 동안 자동으로 영상을 분석합니다. 의심스러운 콘텐츠가 감지되면 즉시 알림을 받게 됩니다.\n\n플로팅 버튼이 숨겨집니다."
            } else {
                "수동 모드에서는 오른쪽 플로팅 버튼을 눌러 직접 영상 분석을 시작할 수 있습니다."
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(boxBg)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF374151)
                )

                if (bgEnabled) {
                    Spacer(Modifier.height(12.dp))

                    if (!hasNls) {
                        Text(
                            text = "• 알림 접근 권한(NLS)이 꺼져 있습니다. 켜야 자동 분석이 동작합니다.",
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFB45309)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        ) { Text("알림 접근 설정 열기", fontSize = 14.sp) }
                    }

                    if (!hasOverlay) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "• 오버레이 권한이 꺼져 있습니다. 켜야 경고 팝업을 띄울 수 있습니다.",
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFB45309)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${ctx.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        ) { Text("오버레이 권한 설정 열기", fontSize = 14.sp) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ✅ 분석 기록 삭제 카드
            val titleColor = if (isEmpty) Color(0xFF6B7280) else Color(0xFF111111)
            val subColor = if (isEmpty) Color(0xFF9CA3AF) else Color(0xFF6B7280)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = clearCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isEmpty) {
                            emptyPulse = true
                            scope.launch {
                                delay(160L)
                                emptyPulse = false
                            }
                            Toast.makeText(ctx, "삭제할 분석 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                repo.clearAllReports()
                                emptyPulse = true
                                delay(160L)
                                emptyPulse = false
                            }
                            Toast.makeText(ctx, "분석 기록을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ✅ 아이콘 배경(Box 배경)은 제거하고, ic_tb.png 자체 배경(연핑크)을 그대로 사용
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp)) // ✅ 완전 원형
                            .background(Color(0xFFFFE4E6)),   // ✅ 스샷처럼 연핑크 원
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tb),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp) // ✅ 원 안에 들어가도록 적당히 축소
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "분석 기록 삭제",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "모든 분석 기록 삭제하기",
                            fontSize = 14.sp,
                            color = subColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 14.dp, top = 0.dp, bottom = 8.dp)
            )

            Text(
                text = "설정",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
        }
    }
}
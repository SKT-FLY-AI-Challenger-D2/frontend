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
import com.example.ytnowplaying.prefs.AuthPrefs
import com.example.ytnowplaying.prefs.ModePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var bgEnabled by remember { mutableStateOf(ModePrefs.isBackgroundModeEnabled(ctx)) }

    val hasNls by remember { mutableStateOf(PermissionChecker.hasNotificationListenerAccess(ctx)) }
    val hasOverlay by remember { mutableStateOf(PermissionChecker.hasOverlayPermission(ctx)) }

    val repo = AppContainer.reportRepository
    val reports by repo.observeReports().collectAsState()
    val isEmpty = reports.isEmpty()

    // ✅ 로그인 상태
    val session by AuthPrefs.session.collectAsState()
    val isLoggedIn = session.isLoggedIn

    var emptyPulse by remember { mutableStateOf(false) }
    val clearCardBg by animateColorAsState(
        targetValue = when {
            isEmpty && emptyPulse -> Color(0xFFE5E7EB)
            isEmpty -> Color(0xFFF3F4F6)
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
                            text = "유튜브를 시청하는 동안 \n자동으로 영상을 분석합니다.",
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
                            if (newValue) OverlayController.stop(ctx)
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFFFE4E6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tb),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
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

            Spacer(Modifier.height(14.dp))

            // ✅ 로그인 / 로그아웃 카드 (UI 생색용)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isLoggedIn) {
                            onOpenLogin()
                        } else {
                            AuthPrefs.logout()
                            Toast.makeText(ctx, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBg = if (!isLoggedIn) Color(0xFFDBEAFE) else Color(0xFFDBEAFE)
                    val iconRes = if (!isLoggedIn) android.R.drawable.ic_input_get else android.R.drawable.ic_lock_power_off
                    val title2 = if (!isLoggedIn) "로그인" else "로그아웃"
                    val sub2 = if (!isLoggedIn) "계정에 로그인하기" else "계정에서 로그아웃하기"

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title2,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111111)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = sub2,
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Text(
                        text = "→",
                        fontSize = 18.sp,
                        color = Color(0xFF9CA3AF)
                    )
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
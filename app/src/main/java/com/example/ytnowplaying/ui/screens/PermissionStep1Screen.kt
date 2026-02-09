package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    // PDF 근거: “먼저 권한을 설정해주세요” + “설정하러 가기”
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("먼저 권한을 설정해주세요", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("알림 접근(Notification access) 권한을 켜주세요.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { SettingsNavigator.openNotificationListenerSettings(ctx) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !granted
        ) {
            Text("설정하러 가기")
        }

        Spacer(Modifier.height(12.dp))
        if (granted) {
            Text("권한이 설정되었습니다. 다음 단계로 이동합니다.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

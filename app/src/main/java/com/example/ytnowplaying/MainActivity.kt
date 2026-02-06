package com.example.ytnowplaying

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MinimalScreen(
                    onOpenPermissionSettings = { openNotificationAccessSettings() }
                )
            }
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

@Composable
private fun MinimalScreen(
    onOpenPermissionSettings: () -> Unit
) {
    Column(Modifier.padding(20.dp)) {
        Text("1) 알림 접근(Notification access) 권한을 켜세요.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenPermissionSettings) {
            Text("권한 설정 열기")
        }
        Spacer(Modifier.height(12.dp))
        Text("2) 유튜브에서 영상을 바꾸면 Logcat(TAG=YTNowPlaying)에 [CONFIRMED] 로그가 찍혀야 합니다.")
    }
}

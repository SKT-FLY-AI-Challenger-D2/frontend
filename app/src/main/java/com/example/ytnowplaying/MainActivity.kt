package com.example.ytnowplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {

    private var permissionOk by mutableStateOf(false)
    private var nowPlaying by mutableStateOf<NowPlayingInfo?>(null)

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_NOW_PLAYING) return

            val title = intent.getStringExtra(EXTRA_TITLE)
            val channel = intent.getStringExtra(EXTRA_CHANNEL)
            val source = intent.getStringExtra(EXTRA_SOURCE)

            val dur = intent.getLongExtra(EXTRA_DURATION_MS, -1L)
            val durationMs = if (dur > 0L) dur else null

            nowPlaying = if (!title.isNullOrBlank()) {
                NowPlayingInfo(
                    title = title,
                    channel = channel,
                    durationMs = durationMs,
                    source = runCatching { NowPlayingInfo.Source.valueOf(source ?: "") }
                        .getOrDefault(NowPlayingInfo.Source.NOTIFICATION)
                )
            } else {
                null
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MainScreen(
                    permissionOk = permissionOk,
                    nowPlaying = nowPlaying,
                    onOpenPermissionSettings = { openNotificationAccessSettings() },
                    onRefresh = { refreshNowPlaying() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ACTION_NOW_PLAYING),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 앱으로 돌아오면 즉시 상태 갱신
        refreshNowPlaying()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun refreshNowPlaying() {
        permissionOk = isNotificationListenerEnabled(this)

        // 권한 OK일 때만 조회 시도 (권한 없으면 getActiveSessions가 SecurityException 날 수 있음)
        nowPlaying = if (permissionOk) {
            NowPlayingFetcher.fetchFromMediaSessions(this) ?: LastKnownNowPlaying.info
        } else {
            null
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = android.content.ComponentName(context, YoutubeNowPlayingListenerService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(cn.flattenToString())
    }
}

@Composable
private fun MainScreen(
    permissionOk: Boolean,
    nowPlaying: NowPlayingInfo?,
    onOpenPermissionSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val status = when {
        !permissionOk -> "상태: 권한 필요(알림 접근)"
        nowPlaying != null -> "상태: 유튜브 재생 감지됨 (${nowPlaying.source.name})"
        else -> "상태: 감지 안됨"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = status, style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = nowPlaying?.title ?: "감지 안됨",
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = nowPlaying?.channel?.let { "채널/아티스트: $it" } ?: "채널/아티스트: (없음)",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "길이: " + (nowPlaying?.durationMs?.let { formatDuration(it) } ?: "감지 안됨"),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onOpenPermissionSettings) {
            Text("권한 설정 열기")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(onClick = onRefresh) {
            Text("새로고침")
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text =
                "프라이버시 안내:\n" +
                        "- 이 앱은 '현재 재생 정보'를 기기 내부에서만 표시합니다.\n" +
                        "- 저장/외부 전송/백그라운드 기록 기능은 없습니다.\n" +
                        "- 유튜브가 시스템에 공개한 미디어 세션/알림 정보만 사용합니다.",
            style = MaterialTheme.typography.bodySmall
        )



    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

package com.example.ytnowplaying

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.ytnowplaying.nav.AppNavHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val TAG = "REALY_AI"

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_REPORT_ID = "extra_open_report_id"
        const val EXTRA_FROM_OVERLAY = "extra_from_overlay"

        const val EXTRA_ALERT_TEXT = "extra_alert_text"
    }


    private var initialOpenReportId by mutableStateOf<String?>(null)
    private var initialFromOverlay by mutableStateOf(false)
    private var initialAlertText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)

        setContent {
            AppNavHost(
                initialOpenReportId = initialOpenReportId,
                initialFromOverlay = initialFromOverlay,
                initialAlertText = initialAlertText,   // ✅ 추가
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(i: Intent) {
        initialOpenReportId = i.getStringExtra(EXTRA_OPEN_REPORT_ID)
        initialFromOverlay = i.getBooleanExtra(EXTRA_FROM_OVERLAY, false)
        initialAlertText = i.getStringExtra(EXTRA_ALERT_TEXT)
    }
}

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


    private fun dumpOverlayIntent(tag: String, i: Intent?) {
        val id = i?.getStringExtra(EXTRA_OPEN_REPORT_ID)
        val from = i?.getBooleanExtra(EXTRA_FROM_OVERLAY, false)
        val alertLen = i?.getStringExtra(EXTRA_ALERT_TEXT)?.length ?: 0
        android.util.Log.d("REALY_AI", "[$tag] openReportId=$id fromOverlay=$from alertLen=$alertLen")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dumpOverlayIntent("ACT-onCreate", intent)
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
        setIntent(intent)
        dumpOverlayIntent("ACT-onNewIntent", intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(i: Intent) {
        // ✅ 오버레이로 "보고서 열기" 인텐트가 아니면 상태를 덮어쓰지 않는다
        if (!i.hasExtra(EXTRA_OPEN_REPORT_ID)) {
            dumpOverlayIntent("ACT-ignoreIntent", i)
            return
        }

        initialOpenReportId = i.getStringExtra(EXTRA_OPEN_REPORT_ID)
        initialFromOverlay = i.getBooleanExtra(EXTRA_FROM_OVERLAY, false)
        initialAlertText = i.getStringExtra(EXTRA_ALERT_TEXT)
    }

}

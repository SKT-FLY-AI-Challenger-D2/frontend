package com.example.ytnowplaying.data.report

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PREF_NAME = "reports_store"
private const val KEY_REPORTS_JSON = "reports_json"
private const val MAX_REPORTS = 10
private const val TAG = "ReportRepo"

class InMemoryReportRepository(
    appCtx: Context,
) : ReportRepository {

    private val ctx = appCtx.applicationContext
    private val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val mutex = Mutex()

    // id -> Report (메모리 캐시)
    private val map = LinkedHashMap<String, Report>()

    // UI에서 바로 구독할 상태
    private val reportsState = MutableStateFlow<List<Report>>(emptyList())

    override fun observeReports(): StateFlow<List<Report>> = reportsState.asStateFlow()

    init {
        loadFromPrefs()
    }

    override suspend fun getReport(id: String): Report? =
        mutex.withLock { map[id] }

    override suspend fun saveReport(report: Report) {
        mutex.withLock {
            map[report.id] = report

            val latest10 = map.values
                .sortedByDescending { it.detectedAtEpochMs }
                .distinctBy { it.id }
                .take(MAX_REPORTS)

            map.clear()
            latest10.forEach { map[it.id] = it }

            reportsState.value = latest10
            persistLocked(latest10)
        }
    }

    override suspend fun listReports(): List<Report> =
        reportsState.value

    // ✅ 추가: 전체 삭제
    override suspend fun clearAllReports() {
        mutex.withLock {
            map.clear()
            reportsState.value = emptyList()
            prefs.edit().remove(KEY_REPORTS_JSON).apply()
        }
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_REPORTS_JSON, null)?.trim()
        if (json.isNullOrEmpty()) return

        try {
            val type = object : TypeToken<List<Report>>() {}.type
            val list: List<Report> = gson.fromJson(json, type) ?: emptyList()

            val latest10 = list
                .sortedByDescending { it.detectedAtEpochMs }
                .distinctBy { it.id }
                .take(MAX_REPORTS)

            map.clear()
            latest10.forEach { map[it.id] = it }
            reportsState.value = latest10

            persistLocked(latest10)
            Log.d(TAG, "loaded reports=${latest10.size}")
        } catch (t: Throwable) {
            Log.w(TAG, "loadFromPrefs failed. clear saved data: ${t.message}")
            prefs.edit().remove(KEY_REPORTS_JSON).apply()
            map.clear()
            reportsState.value = emptyList()
        }
    }

    private fun persistLocked(latest: List<Report>) {
        try {
            val json = gson.toJson(latest)
            prefs.edit().putString(KEY_REPORTS_JSON, json).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "persist failed: ${t.message}")
        }
    }
}
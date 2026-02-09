package com.example.ytnowplaying.nowplaying

import com.example.ytnowplaying.NowPlayingInfo
import java.util.concurrent.atomic.AtomicReference

data class NowPlayingSnapshot(
    val stableKey: String,      // title+duration 기반 (channel 늦게 와도 안정적)
    val title: String,
    val channel: String,        // 채워진 값만 저장
    val durationMs: Long?,
    val updatedAtElapsedMs: Long
)

object NowPlayingCache {
    private val ref = AtomicReference<NowPlayingSnapshot?>(null)

    fun update(stableKey: String, info: NowPlayingInfo) {
        val ch = info.channel?.trim().orEmpty()
        if (ch.isBlank()) return // cache에는 channel 채워진 것만 저장하는 정책

        ref.set(
            NowPlayingSnapshot(
                stableKey = stableKey,
                title = info.title,
                channel = ch,
                durationMs = info.durationMs,
                updatedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            )
        )
    }

    fun get(): NowPlayingSnapshot? = ref.get()

    fun clear() {
        ref.set(null)
    }
}

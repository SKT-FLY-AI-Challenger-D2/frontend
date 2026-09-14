package com.example.ytnowplaying.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPolicyTest {

    private fun decide(
        backgroundModeEnabled: Boolean = false,
        manualButtonEnabled: Boolean = true,
        hasOverlayPermission: Boolean = true,
        playbackStatus: PlaybackStatus = PlaybackStatus.PLAYING,
    ) = OverlayPolicy.decide(backgroundModeEnabled, manualButtonEnabled, hasOverlayPermission, playbackStatus)

    @Test
    fun `재생 중이면 SHOW`() {
        assertEquals(OverlayAction.SHOW, decide(playbackStatus = PlaybackStatus.PLAYING))
    }

    @Test
    fun `버퍼링 중이어도 재생과 동일하게 SHOW`() {
        assertEquals(OverlayAction.SHOW, decide(playbackStatus = PlaybackStatus.BUFFERING))
    }

    @Test
    fun `일시정지_정지면 HIDE`() {
        assertEquals(OverlayAction.HIDE, decide(playbackStatus = PlaybackStatus.PAUSED_OR_STOPPED))
    }

    @Test
    fun `상태를 모르면 HIDE`() {
        assertEquals(OverlayAction.HIDE, decide(playbackStatus = PlaybackStatus.UNKNOWN))
    }

    @Test
    fun `배경모드면 재생 중이어도 HIDE`() {
        assertEquals(
            OverlayAction.HIDE,
            decide(backgroundModeEnabled = true, playbackStatus = PlaybackStatus.PLAYING),
        )
    }

    @Test
    fun `오버레이 권한 없으면 재생 중이어도 HIDE`() {
        assertEquals(
            OverlayAction.HIDE,
            decide(hasOverlayPermission = false, playbackStatus = PlaybackStatus.PLAYING),
        )
    }

    @Test
    fun `수동 버튼 토글이 꺼져 있으면 재생 중이어도 HIDE`() {
        assertEquals(
            OverlayAction.HIDE,
            decide(manualButtonEnabled = false, playbackStatus = PlaybackStatus.PLAYING),
        )
    }
}

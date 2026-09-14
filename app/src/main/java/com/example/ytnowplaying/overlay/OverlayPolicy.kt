package com.example.ytnowplaying.overlay

/**
 * 오버레이 버튼을 보여줄지 숨길지에 대한 순수 판단.
 *
 * 이 함수는 Android 의존이 전혀 없다(테스트 대상은 OverlayPolicyTest 참고). task 생존 여부는
 * 이 함수의 입력이 아니다 — 호출측(YoutubeNowPlayingListenerService.reevaluateAndApply(),
 * FloatingButtonService)이 isAppTaskAlive() 를 먼저 확인해 태스크가 없으면 이 함수를 호출하지
 * 않고 곧바로 HIDE를 적용한다(계획서 §6.1/§7.3). 1초 grace(hysteresis)도 이 함수 밖, 호출측의
 * 타이밍 레이어에서 처리한다(계획서 F1) — decide() 자체는 시간을 모른다.
 */
enum class OverlayAction { SHOW, HIDE }

enum class PlaybackStatus {
    PLAYING,
    BUFFERING,
    PAUSED_OR_STOPPED,
    UNKNOWN,
}

object OverlayPolicy {

    /**
     * @param backgroundModeEnabled 배경모드 ON이면 수동 버튼은 항상 숨긴다.
     * @param manualButtonEnabled 설정 화면의 "돋보기 버튼 표시" 토글.
     * @param hasOverlayPermission SYSTEM_ALERT_WINDOW 권한 보유 여부.
     * @param playbackStatus 현재 유튜브 재생 상태(PLAYING/BUFFERING을 재생 중으로 통일 취급).
     */
    fun decide(
        backgroundModeEnabled: Boolean,
        manualButtonEnabled: Boolean,
        hasOverlayPermission: Boolean,
        playbackStatus: PlaybackStatus,
    ): OverlayAction {
        if (backgroundModeEnabled) return OverlayAction.HIDE
        if (!hasOverlayPermission) return OverlayAction.HIDE
        if (!manualButtonEnabled) return OverlayAction.HIDE
        return when (playbackStatus) {
            PlaybackStatus.PLAYING, PlaybackStatus.BUFFERING -> OverlayAction.SHOW
            PlaybackStatus.PAUSED_OR_STOPPED, PlaybackStatus.UNKNOWN -> OverlayAction.HIDE
        }
    }
}

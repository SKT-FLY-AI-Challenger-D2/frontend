package com.example.ytnowplaying

import android.os.SystemClock
import android.util.Log
import java.util.UUID

/**
 * 계획서 §6.4가 요구하는 구조화 로그 — flowId/path/event/elapsedRealtime/taskAlive.
 *
 * V6-A/V6-B/V16-C 판정 시 "검사 통과 직후의 알려진 경쟁 구간"과 "가드 누락·결함"을 구분하려면,
 * 하나의 분석 시도(flow) 안에서 각 isAppTaskAlive() 검사 지점의 결과와 시각을 재구성할 수 있어야
 * 한다(24라운드 결정). 이 객체는 그 최소 스키마만 제공한다 — 판단 로직은 갖지 않는다.
 *
 * event 는 "그 지점에서 실제로 isAppTaskAlive() 를 호출해 얻은 결과"만 기록한다(사후 추정 금지) —
 * 그래야 이 로그만으로 "검사→행동" 사이의 실제 시차를 신뢰성 있게 재구성할 수 있다.
 */
object FlowLog {
    private const val TAG = "REALY_FLOW"

    fun newFlowId(): String = UUID.randomUUID().toString().take(8)

    fun event(flowId: String, path: String, event: String, taskAlive: Boolean) {
        Log.d(
            TAG,
            "flowId=$flowId path=$path event=$event elapsedRealtime=${SystemClock.elapsedRealtime()} taskAlive=$taskAlive"
        )
    }
}

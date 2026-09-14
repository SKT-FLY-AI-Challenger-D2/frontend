package com.example.ytnowplaying

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * 계획서 §6.4(24~26라운드 확정)가 요구한 구조화 로그를 그대로 구현한다.
 *
 * flowId = 세션ID-path-seq. seq 는 auto/manual 이 "경로별로 별도 카운터를 두지 않고 서비스 프로세스
 * 하나에서 공유"하는 단조증가 정수다(계획서 원문) — 자동/수동이 각자 1부터 발급해 우연히 겹치는 걸
 * 방지하기 위함이다. 세션ID는 서비스 프로세스 생애주기당 한 번만 발급해 앞에 붙인다 — 계획서는 "여러
 * 실행(앱 재시작) 로그를 합쳐 분석해야 할 때만" 붙이라고 했지만, 재시작마다 seq 가 다시 1부터 시작돼
 * 재시작 전후 로그가 같은 번호로 우연히 겹칠 수 있으므로 이 구현은 처음부터 항상 붙인다(요구사항보다
 * 엄격한 쪽으로만 벗어남).
 */
object FlowLog {
    private const val TAG = "REALY_FLOW"

    private val sessionId: String = Random.nextInt(0x1000, 0xFFFF).toString(16)
    private val seq = AtomicInteger(0)

    fun newFlowId(path: String): String = "$sessionId-$path-${seq.incrementAndGet()}"

    /**
     * flow 내부 검사/표시 지점 — 계획서 예시의 `pre_save_guard`/`save_enter` 류 이벤트.
     * event 는 그 지점에서 실제로 수행한 isAppTaskAlive() 결과만 기록한다(사후 추정 금지) — 그래야
     * 이 로그만으로 "검사→행동" 사이의 실제 시차를 신뢰성 있게 재구성할 수 있다.
     */
    fun event(flowId: String, path: String, event: String, taskAlive: Boolean) {
        Log.d(
            TAG,
            "flowId=$flowId path=$path event=$event elapsedRealtime=${SystemClock.elapsedRealtime()} taskAlive=$taskAlive"
        )
    }

    /**
     * §6.4 "task 제거 감지 시각" — 5초 self-poll 이 제거를 알아낸 시각이지 사용자의 실제 스와이프
     * 시각은 아니다(26라운드 한계로 수용됨). 특정 flow 에 종속되지 않는 독립 이벤트이므로 flowId 없이
     * path 와 감지 지점(source)만 기록한다 — 판정 시 같은 elapsedRealtime 축에서 flow 의
     * check/save_enter 시각과 대조한다.
     */
    fun taskRemovalDetected(path: String, source: String) {
        Log.d(
            TAG,
            "path=$path event=task_removal_detected source=$source elapsedRealtime=${SystemClock.elapsedRealtime()}"
        )
    }
}

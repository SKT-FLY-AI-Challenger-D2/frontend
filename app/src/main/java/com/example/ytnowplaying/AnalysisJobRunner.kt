package com.example.ytnowplaying

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 자동분석의 Job/dedup 조정을 전담하는 작은 클래스(계획서 F16, F19 흡수).
 *
 * - 단일 활성 요청(Job+key)을 직접 소유한다. 이중소유를 피하기 위해 호출측(서비스)은 별도로
 *   Job/키 필드를 두지 않는다.
 * - Android/Handler 직접 의존이 없다 — 완료-콜백 실행 지점은 [poster] 람다로 추상화한다.
 *   운영 코드는 `{ block -> mainHandler.post(block) }` 를 넘기고, 테스트는 즉시실행 람다나
 *   수동으로 비우는 큐를 넘긴다.
 * - "취소"와 "자기 완료"가 같은 요청을 놓고 경쟁할 때, [AtomicBoolean] CAS 로 정확히 한쪽만
 *   그 요청의 결과(참조 정리, dedup 복원 여부)를 확정하도록 중재한다. CAS 위치가 onOutcome
 *   완료 시점보다 늦어 남는 잔여 경합은 dedup 타이밍에만 영향을 주며, 저장/표시의 정확성은
 *   [onOutcome] 자신이 저장 직전 재검사로 보장한다(계획서 21라운드, §9 한계 참조).
 */
class AnalysisJobRunner<R>(
    private val scope: CoroutineScope,
    private val poster: (() -> Unit) -> Unit,
) {

    private class ActiveRequest(val job: Job, val key: String) {
        val resolved = AtomicBoolean(false)
    }

    private var current: ActiveRequest? = null

    /**
     * 새 분석 요청을 제출한다.
     *
     * 같은 [key] 로 이미 활성 요청이 있으면(KEEP_CURRENT) 아무것도 하지 않는다. 다른 key 면
     * 기존 요청을 정리(REPLACE_OR_START, dedup 비복원)하고 새 Job 을 시작한다.
     *
     * @param analyzer 실제 분석 요청(suspend). 예외를 던지면 [onOutcome] 은 호출되지 않는다.
     * @param onOutcome 결과를 받아 저장/표시 등 실제 적용을 수행하고, 적용에 성공했으면 true를
     *   반환한다(applied). 저장이 예외 없이 성공한 뒤, 또는 오류 표시가 정상 실행된 뒤에만
     *   true 를 반환해야 한다 — 시도 직전에 미리 확정하면 안 된다.
     * @param onDedupRestore 정상 적용되지 못한 요청의 재전송 차단 상태를 되돌릴 때 호출된다.
     */
    fun submit(
        key: String,
        analyzer: suspend () -> R,
        onOutcome: suspend (key: String, result: R?) -> Boolean,
        onDedupRestore: (key: String) -> Unit,
    ) {
        if (current?.key == key && current?.job?.isActive == true) {
            return // KEEP_CURRENT
        }
        cancelActive(restoreDedup = false, onDedupRestore = onDedupRestore) // REPLACE_OR_START

        var applied = false
        lateinit var record: ActiveRequest

        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = analyzer()
            applied = onOutcome(key, result)
        }

        record = ActiveRequest(job, key)
        current = record

        job.invokeOnCompletion {
            // 성공/실패/취소 등 모든 종료 경로에서 호출된다. 그러나 CAS를 이긴 쪽만 실제로 처리한다.
            if (record.resolved.compareAndSet(false, true)) {
                poster {
                    if (current === record) current = null
                    if (!applied) onDedupRestore(key)
                }
            }
            // CAS 실패 = cancelActive() 가 이미 이 요청을 확정했음 — 여기서는 아무것도 안 함.
        }

        job.start()
    }

    /**
     * 현재 활성 요청을 취소한다. 이미 스스로 완료(또는 완료 중)된 요청이면 아무것도 하지
     * 않는다 — 그 요청의 [submit] 쪽 완료 콜백이 CAS 를 이겨 스스로 올바르게 정리한다.
     *
     * @param restoreDedup true 면(주로 task 제거) 취소된 요청의 dedup 상태를 되돌린다. false 면
     *   (주로 다른 key 로의 교체) 건드리지 않는다 — 새 key 가 이미 dedup 상태를 덮어썼을 수
     *   있으므로 굳이 복원할 필요가 없다.
     */
    fun cancelActive(restoreDedup: Boolean, onDedupRestore: (key: String) -> Unit) {
        val record = current ?: return
        if (record.resolved.compareAndSet(false, true)) {
            // CAS 승리 = 이 Job이 스스로 완료되기 전에 우리가 먼저 확정함 — 진짜로 취소 처리.
            poster { if (current === record) current = null }
            if (restoreDedup) onDedupRestore(record.key)
            record.job.cancel()
        }
        // CAS 실패 = Job이 이미 스스로 완료(또는 완료 중)돼 그쪽 완료 콜백이 이미 이겼거나 곧
        // 이김 — 여기서는 아무것도 안 함(cancel()도 불필요, 이미 끝났으므로).
    }
}

package com.example.ytnowplaying

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisJobRunnerTest {

    /**
     * Dispatchers.Unconfined + 즉시실행 poster: 중단점 없는 analyzer는 submit() 호출 안에서
     * 동기적으로 끝까지 실행되므로, 별도 join/yield 없이 결정적으로 검증할 수 있다
     * (계획서 §8.1 V9-b/V27).
     */
    private fun immediatePosterRunner(): AnalysisJobRunner<String> =
        AnalysisJobRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            poster = { it() },
        )

    @Test
    fun `같은 key로 재확인해도 아직 활성 상태면 KEEP_CURRENT`() {
        val runner = immediatePosterRunner()
        val deferred = CompletableDeferred<String>()
        var callCount = 0
        val restored = mutableListOf<String>()

        runner.submit(
            key = "A",
            analyzer = { callCount++; deferred.await() },
            onOutcome = { _, _ -> true },
            onDedupRestore = { restored += it },
        )
        assertEquals(1, callCount)

        // 같은 key로 다시 제출 — A가 아직 deferred에서 대기 중(활성 상태)이어야 한다.
        runner.submit(
            key = "A",
            analyzer = { callCount++; deferred.await() },
            onOutcome = { _, _ -> true },
            onDedupRestore = { restored += it },
        )

        assertEquals(1, callCount) // 재호출 안 됨 = KEEP_CURRENT
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `다른 key로 전환하면 기존 Job은 취소되고 dedup은 복원되지 않는다`() {
        val runner = immediatePosterRunner()
        val deferredA = CompletableDeferred<String>()
        val restored = mutableListOf<String>()

        runner.submit(
            key = "A",
            analyzer = { deferredA.await() },
            onOutcome = { _, _ -> true },
            onDedupRestore = { restored += it },
        )

        runner.submit(
            key = "B",
            analyzer = { "b-result" },
            onOutcome = { _, _ -> true },
            onDedupRestore = { restored += it },
        )

        // A는 REPLACE_OR_START로 취소되지만 restoreDedup=false라 복원 콜백이 오지 않는다(F12).
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `task 제거로 취소되면 dedup이 복원된다`() {
        val runner = immediatePosterRunner()
        val deferredA = CompletableDeferred<String>()
        val restored = mutableListOf<String>()

        runner.submit(
            key = "A",
            analyzer = { deferredA.await() },
            onOutcome = { _, _ -> true },
            onDedupRestore = { restored += it },
        )

        runner.cancelActive(restoreDedup = true, onDedupRestore = { restored += it })

        assertEquals(listOf("A"), restored)
    }

    @Test
    fun `정상 완료된 요청은 dedup을 유지한다`() {
        val runner = immediatePosterRunner()
        val restored = mutableListOf<String>()

        runner.submit(
            key = "A",
            analyzer = { "ok" },
            onOutcome = { _, _ -> true }, // 저장 성공 = applied
            onDedupRestore = { restored += it },
        )

        assertTrue(restored.isEmpty())
    }

    @Test
    fun `늦은 완료 콜백이 새로 등록된 활성 요청의 참조를 지우지 않는다 (V27)`() {
        val pending = mutableListOf<() -> Unit>()
        val runner = AnalysisJobRunner<String>(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            poster = { pending += it },
        )

        var callCountA = 0
        var callCountB = 0
        val bDeferred = CompletableDeferred<String>()

        // A는 중단점 없이 즉시 완료 — 완료 콜백은 poster 큐에 쌓인 채 아직 실행되지 않는다.
        runner.submit(
            key = "A",
            analyzer = { callCountA++; "a-result" },
            onOutcome = { _, _ -> true },
            onDedupRestore = {},
        )
        assertEquals(1, callCountA)
        assertEquals(1, pending.size) // A의 완료 콜백이 대기 중

        // B는 deferred에서 진짜로 계속 활성 상태를 유지한다(취소된 적 없음).
        runner.submit(
            key = "B",
            analyzer = { callCountB++; bDeferred.await() },
            onOutcome = { _, _ -> true },
            onDedupRestore = {},
        )
        assertEquals(1, callCountB)

        // A의 늦은 완료 콜백을 이제야 실행 — B 참조에 영향이 없어야 한다.
        val aCompletion = pending.removeAt(0)
        aCompletion()

        // 같은 B를 다시 제출 — B가 여전히 활성 상태라면 KEEP_CURRENT로 analyzer가 재호출되지 않는다.
        runner.submit(
            key = "B",
            analyzer = { callCountB++; bDeferred.await() },
            onOutcome = { _, _ -> true },
            onDedupRestore = {},
        )

        assertEquals(1, callCountB) // 재호출 안 됨 = B가 여전히 활성 상태로 유지됨
    }
}

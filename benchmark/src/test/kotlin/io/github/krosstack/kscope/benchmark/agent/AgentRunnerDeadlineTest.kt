package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.agent.launch.NoopExecutor
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class AgentRunnerDeadlineTest : AgentRunnerFixture() {
    @Test
    fun `the global limit also bounds the verification`() {
        val task = prepare()
        val store = RunStore(out)
        val verifier = FakeVerifier()

        runnerWith(store, verifier, Duration.ofMinutes(2)).run(listOf(task), false)

        val limit = assertNotNull(verifier.limits.single())
        assertTrue(limit <= Duration.ofMinutes(2), "$limit")
    }

    @Test
    fun `a verification that the global limit cut short is a failed run and is retried`() {
        val task = prepare()
        val store = RunStore(out)
        val verifier = FakeVerifier(needs = Duration.ofMinutes(10))

        val summary = runnerWith(store, verifier, Duration.ofMinutes(2)).run(listOf(task), false)

        val record = assertNotNull(store.load("T01-A-r1"))
        assertEquals(listOf("T01-A-r1"), summary.failed)
        assertEquals(RunStatus.FAILED, record.status)
        assertEquals(FailureCode.TIMEOUT, record.failure?.code)
        val retried = AgentRunner(settings(), store, single(NoopExecutor(), store)) {}
        assertEquals(listOf("T01-A-r1"), retried.run(listOf(task), false).completed)
    }

    @Test
    fun `a verification that runs out of its own limit stays a completed run`() {
        val task = prepare()
        val store = RunStore(out)
        val verifier = FakeVerifier(needs = Duration.ofMinutes(60))

        val summary = runnerWith(store, verifier).run(listOf(task), false)

        assertEquals(listOf("T01-A-r1"), summary.completed)
        assertEquals(true, store.load("T01-A-r1")?.verification?.timedOut)
    }
}

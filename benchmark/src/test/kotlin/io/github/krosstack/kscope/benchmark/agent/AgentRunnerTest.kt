package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.agent.launch.AgentExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.AgentRequest
import io.github.krosstack.kscope.benchmark.agent.launch.AgentRun
import io.github.krosstack.kscope.benchmark.agent.launch.NoopExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.ReferenceExecutor
import io.github.krosstack.kscope.benchmark.agent.telemetry.AgentTelemetry
import io.github.krosstack.kscope.benchmark.agent.telemetry.ParseContext
import io.github.krosstack.kscope.benchmark.agent.workspace.ContextPreparer
import io.github.krosstack.kscope.context.TokenEstimators
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AgentRunnerTest : AgentRunnerFixture() {
    private class FailingExecutor : AgentExecutor {
        override val name = "claude"
        override val model: String? = "sonnet"
        override val dryRun = false

        override fun execute(request: AgentRequest, context: ParseContext, timeout: Duration) =
            AgentRun(RunFailure(FailureCode.AUTHENTICATION, "expired"), AgentTelemetry(inputTokens = 5))
    }

    @Test
    fun `without a change verification fails and with the reference patch it passes`() {
        val task = prepare()
        val store = RunStore(out)
        val noop = AgentRunner(settings(), store, single(NoopExecutor(), store)) {}.run(listOf(task), dryRun = true)

        assertTrue(noop.clean, noop.mismatches.toString())
        assertEquals(false, store.load("T01-A-r1")?.verification?.passed)

        val reference = RunStore(base.resolve("out-reference"))
        val referenceRunner = AgentRunner(settings(), reference, single(ReferenceExecutor(), reference)) {}
        val summary = referenceRunner.run(listOf(task), true)

        assertTrue(summary.clean, summary.mismatches.toString())
        val record = assertNotNull(reference.load("T01-A-r1"))
        assertEquals(true, record.verification?.passed)
        assertEquals(listOf("src/Greeting.txt"), record.gold?.goldFilesHit)
        assertEquals(emptyList(), record.gold?.unnecessaryFiles)
        assertTrue(record.dryRun)
    }

    @Test
    fun `completed runs are kept when the command is issued again`() {
        val task = prepare()
        val store = RunStore(out)
        val executor = NoopExecutor()
        AgentRunner(settings(), store, single(executor, store)) {}.run(listOf(task), true)

        val again = AgentRunner(settings(), store, single(executor, store)) {}.run(listOf(task), true)

        assertEquals(listOf("T01-A-r1"), again.reused)
        assertTrue(again.completed.isEmpty())
    }

    @Test
    fun `an agent failure is a failed run without verification and is retried on the next issue`() {
        val task = prepare()
        val store = RunStore(out)
        val first = AgentRunner(settings(), store, single(FailingExecutor(), store)) {}.run(listOf(task), false)

        val record = assertNotNull(store.load("T01-A-r1"))
        assertEquals(RunStatus.FAILED, record.status)
        assertEquals(FailureCode.AUTHENTICATION, record.failure?.code)
        assertNull(record.verification)
        assertEquals(5, record.telemetry.inputTokens)
        assertEquals(listOf("T01-A-r1"), first.failed)

        val keeper = AgentRunner(settings(keepFailed = true), store, single(NoopExecutor(), store)) {}
        val kept = keeper.run(listOf(task), false)
        assertEquals(listOf("T01-A-r1"), kept.reused)
        val retried = AgentRunner(settings(), store, single(NoopExecutor(), store)) {}.run(listOf(task), false)
        assertEquals(listOf("T01-A-r1"), retried.completed)
    }

    @Test
    fun `runs that the global cap leaves out are reported as not started`() {
        val task = prepare()
        val store = RunStore(out)

        val capped = AgentRunner(settings(cap = Duration.ZERO), store, single(NoopExecutor(), store)) {}
        val summary = capped.run(listOf(task), false)

        assertEquals(listOf("T01-A-r1"), summary.notStarted)
        assertFalse(summary.clean)
        assertNull(store.load("T01-A-r1"))
    }

    @Test
    fun `a dry run whose verification contradicts its stand-in is a mismatch`() {
        val task = prepare()
        val store = RunStore(out)
        val reference = ReferenceExecutor()
        Files.writeString(task.referencePatch, "not a patch\n")

        val summary = AgentRunner(settings(), store, single(reference, store)) {}.run(listOf(task), true)

        assertFalse(summary.clean)
        assertEquals(RunStatus.FAILED, store.load("T01-A-r1")?.status)
    }

    @Test
    fun `workspace copies and logs of a run lie outside the repository and carry no hidden material`() {
        val task = prepare()
        val store = RunStore(out)
        val environment = RunEnvironment(source, temp, store::logDirectory, keepWorkspaces = true)
        val contexts = ContextPreparer(2000, TokenEstimators.create())
        val single = SingleRun(environment, NoopExecutor(), FakeVerifier(), contexts)

        single.execute(task, Arm.A, 1, Duration.ofMinutes(1))

        val workspace = temp.resolve("T01-A-r1/workspace")
        assertTrue(Files.isDirectory(workspace))
        assertFalse(workspace.toRealPath().startsWith(source.toRealPath()))
        assertEquals("1", git.output(workspace, "rev-list", "--count", "HEAD").trim())
        assertFalse(Files.exists(workspace.resolve("reference.patch")))
        assertTrue(Files.readString(store.logDirectory("T01-A-r1").resolve("prompt.txt")).contains(spec.statement))
    }

    @Test
    fun `the binding rejects a run directory of another agent`() {
        val store = RunStore(out)
        val binding = RunBinding(
            agent = "claude",
            model = "sonnet",
            budget = 4000,
            estimator = "o200k_base",
            promptVersion = "p",
            dryRun = false,
        )

        assertNull(store.bind(binding))
        assertNull(store.bind(binding))
        assertNotNull(store.bind(binding.copy(model = "opus")))
        assertEquals(binding, store.binding())
    }

    @Test
    fun `the plan blocks the arms of one task and repeat and is the same for the same seed`() {
        val task = prepare()
        val first = RunPlan.build(listOf(task), Arm.entries, 2, 5).map { it.key }
        val second = RunPlan.build(listOf(task), Arm.entries, 2, 5).map { it.key }

        assertEquals(first, second)
        assertEquals(setOf("T01-A-r1", "T01-B-r1", "T01-C-r1"), first.take(3).toSet())
        assertEquals(6, first.toSet().size)
    }
}

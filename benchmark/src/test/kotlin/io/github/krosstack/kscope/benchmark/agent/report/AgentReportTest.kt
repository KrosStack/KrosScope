package io.github.krosstack.kscope.benchmark.agent.report

import io.github.krosstack.kscope.benchmark.agent.AgentTaskSpec
import io.github.krosstack.kscope.benchmark.agent.Arm
import io.github.krosstack.kscope.benchmark.agent.CompileStatus
import io.github.krosstack.kscope.benchmark.agent.ContextRecord
import io.github.krosstack.kscope.benchmark.agent.FailureCode
import io.github.krosstack.kscope.benchmark.agent.GoldRecord
import io.github.krosstack.kscope.benchmark.agent.RunBinding
import io.github.krosstack.kscope.benchmark.agent.RunFailure
import io.github.krosstack.kscope.benchmark.agent.RunRecord
import io.github.krosstack.kscope.benchmark.agent.RunStatus
import io.github.krosstack.kscope.benchmark.agent.RunTiming
import io.github.krosstack.kscope.benchmark.agent.VerificationRecord
import io.github.krosstack.kscope.benchmark.agent.VerifySpec
import io.github.krosstack.kscope.benchmark.agent.telemetry.AgentTelemetry
import io.github.krosstack.kscope.benchmark.agent.telemetry.Milestone
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentReportTest {
    private data class Shape(
        val passed: Boolean = true,
        val tokens: Long = 1000,
        val cost: Double = 0.5,
        val failure: FailureCode? = null,
    )

    private fun spec(id: String) = AgentTaskSpec(
        id = id,
        title = "Task $id",
        category = "example",
        statement = "Private statement text $id",
        goldFiles = listOf("a/One.kt", "a/Two.kt"),
        verify = VerifySpec(listOf(":m:test")),
        failToPass = listOf("p.T#x"),
        passToPass = emptyList(),
    )

    private fun record(task: String, arm: Arm, repeat: Int = 1, shape: Shape = Shape()): RunRecord {
        val failure = shape.failure
        val warnings = if (arm == Arm.C && repeat == 2) listOf("unexpected tools: Bash") else emptyList()
        return RunRecord(
            taskId = task,
            arm = arm,
            repeat = repeat,
            agent = "claude",
            model = "sonnet",
            dryRun = false,
            status = if (failure == null) RunStatus.COMPLETED else RunStatus.FAILED,
            failure = failure?.let { RunFailure(it, "C:\\private\\detail") },
            context = ContextRecord(tokens = if (arm == Arm.A) 0 else 3900, budget = 4000, estimator = "o200k_base"),
            telemetry = AgentTelemetry(
                inputTokens = shape.tokens / 2,
                cacheReadTokens = shape.tokens / 2,
                costUsd = shape.cost,
                turns = 4,
                wallMs = 10_000,
                toolCalls = mapOf("Read" to 3, "Edit" to 1),
                firstGoldRead = Milestone(2, 500),
                warnings = warnings,
            ),
            verification = if (failure == null) {
                VerificationRecord(shape.passed, CompileStatus.PASSED, if (shape.passed) 1 else 0, 1)
            } else {
                null
            },
            gold = GoldRecord(
                modifiedFiles = listOf("a/One.kt", "x/Extra.kt"),
                goldFilesHit = listOf("a/One.kt"),
                unnecessaryFiles = listOf("x/Extra.kt"),
            ),
            timing = RunTiming("2026-01-01T00:00:00Z"),
        )
    }

    private val binding = RunBinding(
        agent = "claude",
        model = "sonnet",
        budget = 4000,
        estimator = "o200k_base",
        promptVersion = "p8-1",
        dryRun = false,
    )

    private fun report(records: List<RunRecord>, repeats: Int = 2) = AgentReportBuilder.build(
        ReportInput(listOf(spec("T01"), spec("T02")), Arm.entries, repeats, records, binding),
    )

    private fun describe(issue: RunIssue) = "${issue.taskId} ${issue.arm.letter} ${issue.repeat} ${issue.reason}"

    @Test
    fun `success is shown beside tokens, cost and turns for every task and arm`() {
        val records = listOf(
            record("T01", Arm.A, shape = Shape(tokens = 2000, cost = 1.0)),
            record("T01", Arm.C, shape = Shape(tokens = 1000, cost = 0.5)),
            record("T01", Arm.B, shape = Shape(passed = false, tokens = 3000, cost = 1.5)),
            record("T02", Arm.A, shape = Shape(tokens = 4000, cost = 2.0)),
            record("T02", Arm.C, shape = Shape(tokens = 1000, cost = 0.5)),
        )

        val aggregate = report(records, repeats = 1)

        val cellA = aggregate.tasks.first { it.taskId == "T01" }.cells.first { it.arm == Arm.A }
        assertEquals(1, cellA.successes)
        assertEquals(2000.0, cellA.meanTotalTokens)
        assertEquals(0.5, cellA.meanGoldRecall)
        assertEquals(1.0, cellA.meanUnnecessaryFiles)
        assertEquals(2.0, cellA.meanFirstGoldReadEvent)
        val armC = aggregate.arms.first { it.arm == Arm.C }
        assertEquals(2, armC.cell.successes)
        assertEquals(sqrt(0.125), assertNotNull(armC.tokenRatioToA), 1e-9)
        assertEquals(1000.0, assertNotNull(armC.tokensPerSolvedTask), 1e-9)
        assertEquals(null, aggregate.arms.first { it.arm == Arm.A }.tokenRatioToA)
    }

    @Test
    fun `failed and missing runs are listed and excluded from success`() {
        val records = listOf(
            record("T01", Arm.A),
            record("T01", Arm.B, shape = Shape(failure = FailureCode.TIMEOUT)),
            record("T01", Arm.C, repeat = 2),
        )

        val aggregate = report(records)

        assertEquals(listOf("T01 B 1 run timeout"), aggregate.failedRuns.map(::describe))
        assertEquals(9, aggregate.missingRuns.size)
        val cellB = aggregate.tasks.first { it.taskId == "T01" }.cells.first { it.arm == Arm.B }
        assertEquals(0, cellB.completed)
        assertEquals(1, cellB.failed)
        assertEquals(null, cellB.passRate)
        assertEquals(listOf("T01 C 2 unexpected tools: Bash"), aggregate.warningRuns.map(::describe))
    }

    @Test
    fun `the markdown is publishable and shows failed and missing runs`() {
        val failed = Shape(failure = FailureCode.AUTHENTICATION)
        val records = listOf(record("T01", Arm.A), record("T01", Arm.B, shape = failed))

        val markdown = AgentReportMarkdown.render(report(records, repeats = 1))

        assertTrue(markdown.contains("## Failed runs"))
        assertTrue(markdown.contains("T01 arm B repeat 1: authentication error"))
        assertTrue(markdown.contains("## Missing runs"))
        assertTrue(markdown.contains("| T01 Task T01 | A | 1/1 | 1/1 |"))
        val leak = Regex("[A-Za-z]:\\\\|/Users/|/home/|private|Private statement|\\.kt\\b")
        assertFalse(leak.containsMatchIn(markdown), markdown)
    }

    @Test
    fun `a dry run is labelled as such`() {
        val stand = binding.copy(agent = "noop", model = null, dryRun = true)
        val aggregate = AgentReportBuilder.build(
            ReportInput(listOf(spec("T01")), listOf(Arm.A), 1, listOf(record("T01", Arm.A)), stand),
        )

        assertTrue(AgentReportMarkdown.render(aggregate).contains("Dry run with the stand-in agent `noop`"))
    }

    @Test
    fun `a retrieval that found no file counts as zero context recall for a context arm and not for arm A`() {
        fun retrieved(task: String, arm: Arm, files: List<String>) = record(task, arm).let {
            it.copy(context = it.context.copy(files = files, goldFilesInContext = files.size))
        }
        val records = listOf(
            retrieved("T01", Arm.B, emptyList()),
            retrieved("T02", Arm.B, listOf("a/One.kt", "a/Two.kt")),
            retrieved("T01", Arm.A, emptyList()),
        )

        val aggregate = report(records, repeats = 1)

        val armB = aggregate.arms.first { it.arm == Arm.B }.cell
        assertEquals(0.5, armB.meanContextRecall)
        assertEquals(1.0, armB.meanContextPrecision)
        assertEquals(null, aggregate.arms.first { it.arm == Arm.A }.cell.meanContextRecall)
    }
}

package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.FailureCode
import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeFixtures.FINISHED
import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeFixtures.INIT_EVENT
import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeFixtures.assistant
import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeFixtures.lines
import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeFixtures.toolResult
import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeFixtures.toolUse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClaudeEventParserTest {
    private val context = ParseContext(
        goldFiles = setOf("core/A.kt"),
        knownFiles = setOf("core/A.kt", "core/B.kt"),
        workspaceRoot = "/scratch/run/workspace",
    )

    @Test
    fun `a FINISHED run reports tokens, cost, turns, tools and files`() {
        val parsed = ClaudeEventParser().parse(
            lines(
                INIT_EVENT,
                assistant("m1", toolUse("t1", "Read", "/scratch/run/workspace/core/A.kt")),
                toolResult("t1", "class A"),
                assistant("m2", toolUse("t2", "Edit", "/scratch/run/workspace/core/A.kt")),
                toolResult("t2", "failed", error = true),
                assistant("m3", toolUse("t3", "Grep", "core")),
                FINISHED,
            ),
            context,
        )

        val telemetry = assertIs<ParsedRun.Finished>(parsed).telemetry
        assertEquals(20, telemetry.inputTokens)
        assertEquals(200, telemetry.cacheCreationTokens)
        assertEquals(300, telemetry.cacheReadTokens)
        assertEquals(50, telemetry.outputTokens)
        assertEquals(7, telemetry.reasoningTokens)
        assertEquals(0.0123, telemetry.costUsd)
        assertEquals(3, telemetry.turns)
        assertEquals(900L, telemetry.apiMs)
        assertEquals(mapOf("Edit" to 1, "Grep" to 1, "Read" to 1), telemetry.toolCalls)
        assertEquals(1, telemetry.toolErrors)
        assertEquals(listOf("core/A.kt"), telemetry.filesRead)
        assertEquals(listOf("core/A.kt"), telemetry.filesEdited)
        assertEquals(Milestone(1, 100L), telemetry.firstGoldRead)
        assertEquals(Milestone(3, 300L), telemetry.firstGoldEdit)
        assertEquals("9.9.9", telemetry.observed?.version)
        assertTrue(telemetry.warnings.isEmpty())
    }

    @Test
    fun `unknown event types and invalid lines are counted and tolerated`() {
        val unknown = """{"type":"future_event","x":1}"""
        val noType = """{"no_type":true}"""

        val parsed = ClaudeEventParser().parse(lines(INIT_EVENT, unknown, "not json", noType, FINISHED), context)

        val telemetry = assertIs<ParsedRun.Finished>(parsed).telemetry
        assertEquals(mapOf("future_event" to 1, "invalid-json" to 1, "missing-type" to 1), telemetry.unknownEventTypes)
    }

    @Test
    fun `an authentication failure is a failed run with its reason`() {
        val parsed = ClaudeEventParser().parse(
            lines(
                INIT_EVENT,
                ClaudeFixtures.AUTHENTICATION_RETRY,
                ClaudeFixtures.SYNTHETIC_MESSAGE,
                ClaudeFixtures.AUTHENTICATION_FAILURE,
            ),
            context,
        )

        val failed = assertIs<ParsedRun.Failed>(parsed)
        assertEquals(FailureCode.AUTHENTICATION, failed.failure.code)
        assertEquals(1, failed.telemetry.apiRetries)
        assertEquals(0, failed.telemetry.totalTokens)
    }

    @Test
    fun `an error result without an authentication hint is an agent error`() {
        val error = """{"type":"result","subtype":"error_during_execution","is_error":true,"result":"boom"}"""

        val parsed = ClaudeEventParser().parse(lines(INIT_EVENT, error), context)

        assertEquals(FailureCode.AGENT_ERROR, assertIs<ParsedRun.Failed>(parsed).failure.code)
    }

    @Test
    fun `a stream without a result is unfinished and totals come from the messages`() {
        val secondUsage = """{"input_tokens":2,"cache_read_input_tokens":4}"""
        val parsed = ClaudeEventParser().parse(
            lines(
                INIT_EVENT,
                assistant("m1", toolUse("t1", "Read", "core/B.kt"), """{"input_tokens":5,"output_tokens":6}"""),
                assistant("m1", toolUse("t1b", "Read", "core/B.kt"), """{"input_tokens":5,"output_tokens":9}"""),
                assistant("m2", toolUse("t2", "Read", "core/A.kt"), secondUsage),
            ),
            context,
        )

        val telemetry = assertIs<ParsedRun.Unfinished>(parsed).telemetry
        assertEquals(7, telemetry.inputTokens)
        assertEquals(9, telemetry.outputTokens)
        assertEquals(4, telemetry.cacheReadTokens)
        assertEquals(2, telemetry.turns)
        assertEquals(listOf("core/A.kt", "core/B.kt"), telemetry.filesRead)
        assertNotNull(telemetry.firstGoldRead)
    }

    @Test
    fun `a result without usage or optional fields still finishes`() {
        val parsed = ClaudeEventParser().parse(lines("""{"type":"result","subtype":"success"}"""), context)

        val telemetry = assertIs<ParsedRun.Finished>(parsed).telemetry
        assertEquals(0, telemetry.totalTokens)
        assertEquals(null, telemetry.costUsd)
    }

    @Test
    fun `tools beyond the allowed set are reported as a warning`() {
        val withShell = INIT_EVENT.replace("\"Write\"]", "\"Write\",\"Bash\"]")

        val parsed = ClaudeEventParser().parse(lines(withShell, FINISHED), context)

        assertTrue(assertIs<ParsedRun.Finished>(parsed).telemetry.warnings.single().contains("Bash"))
    }

    @Test
    fun `paths outside the workspace are not mistaken for workspace files`() {
        val parsed = ClaudeEventParser().parse(
            lines(INIT_EVENT, assistant("m1", toolUse("t1", "Read", "/elsewhere/other.kt")), FINISHED),
            context,
        )

        assertEquals(listOf("<outside-workspace>"), assertIs<ParsedRun.Finished>(parsed).telemetry.filesRead)
    }
}

package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.FailureCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CodexEventParserTest {
    private val context = ParseContext(setOf("core/A.kt"), setOf("core/A.kt", "core/B.kt"), "/scratch/run/workspace")

    private fun lines(vararg text: String) = text.mapIndexed { index, line -> TimedLine(index, index * 50L, line) }

    private val started = """{"type":"thread.started","thread_id":"t"}"""

    private val turn = """{"type":"turn.started"}"""

    private fun item(body: String) = """{"type":"item.completed","item":$body}"""

    private fun command(text: String, output: String, exit: Int) =
        item("""{"type":"command_execution","command":"$text","aggregated_output":"$output","exit_code":$exit}""")

    private fun usage(input: Int, cached: Int, output: Int) =
        """{"type":"turn.completed","usage":{"input_tokens":$input,""" +
            """"cached_input_tokens":$cached,"output_tokens":$output}}"""

    @Test
    fun `a completed turn reports uncached input, cache reads and output`() {
        val parsed = CodexEventParser().parse(
            lines(
                started,
                turn,
                item("""{"id":"i1","type":"reasoning","text":"thinking"}"""),
                command("cat core/A.kt", "class A", 0),
                command("cat missing.kt", "", 1),
                item("""{"id":"i4","type":"file_change","changes":[{"path":"core/A.kt","kind":"update"}]}"""),
                item("""{"id":"i5","type":"agent_message","text":"done"}"""),
                usage(1000, 800, 50),
            ),
            context,
        )

        val telemetry = assertIs<ParsedRun.Finished>(parsed).telemetry
        assertEquals(200, telemetry.inputTokens)
        assertEquals(800, telemetry.cacheReadTokens)
        assertEquals(50, telemetry.outputTokens)
        assertEquals(1, telemetry.turns)
        assertEquals(mapOf("command_execution" to 2, "file_change" to 1), telemetry.toolCalls)
        assertEquals(1, telemetry.toolErrors)
        assertEquals(listOf("core/A.kt"), telemetry.filesRead)
        assertEquals(listOf("core/A.kt"), telemetry.filesEdited)
        assertEquals(Milestone(3, 150L), telemetry.firstGoldRead)
        assertEquals(Milestone(5, 250L), telemetry.firstGoldEdit)
    }

    @Test
    fun `a failed turn with an unauthorized message is an authentication failure`() {
        val parsed = CodexEventParser().parse(
            lines(started, turn, """{"type":"turn.failed","error":{"message":"unexpected status 401 Unauthorized"}}"""),
            context,
        )

        assertEquals(FailureCode.AUTHENTICATION, assertIs<ParsedRun.Failed>(parsed).failure.code)
    }

    @Test
    fun `other turn failures are agent errors and unknown events are tolerated`() {
        val parsed = CodexEventParser().parse(
            lines(
                started,
                """{"type":"error","message":"stream disconnected"}""",
                """{"type":"later.event"}""",
                """{"type":"turn.failed"}""",
            ),
            context,
        )

        val failed = assertIs<ParsedRun.Failed>(parsed)
        assertEquals(FailureCode.AGENT_ERROR, failed.failure.code)
        assertEquals(mapOf("later.event" to 1), failed.telemetry.unknownEventTypes)
    }

    @Test
    fun `a stream that stops before a turn completes is unfinished`() {
        val parsed = CodexEventParser().parse(lines(started, turn), context)

        assertIs<ParsedRun.Unfinished>(parsed)
    }

    @Test
    fun `other item types count as tool calls of their own type`() {
        val parsed = CodexEventParser().parse(
            lines(started, item("""{"id":"i","type":"web_search","query":"q"}"""), usage(0, 0, 0)),
            context,
        )

        assertEquals(mapOf("web_search" to 1), assertIs<ParsedRun.Finished>(parsed).telemetry.toolCalls)
    }
}

package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.items
import io.github.krosstack.kscope.benchmark.agent.long
import io.github.krosstack.kscope.benchmark.agent.obj
import io.github.krosstack.kscope.benchmark.agent.parseJsonObject
import io.github.krosstack.kscope.benchmark.agent.string
import kotlinx.serialization.json.JsonObject

internal class CodexEventParser : EventParser {
    override fun parse(lines: List<TimedLine>, context: ParseContext): ParsedRun {
        val accumulator = TelemetryAccumulator(context)
        val state = CodexStreamState()
        lines.forEach { line -> handle(line, accumulator, state, context) }
        val telemetry = accumulator.build(state.telemetry())
        val failure = state.failure
        return when {
            failure != null -> ParsedRun.Failed(telemetry, failure)
            state.turnsCompleted > 0 -> ParsedRun.Finished(telemetry)
            else -> ParsedRun.Unfinished(telemetry)
        }
    }

    private fun handle(
        line: TimedLine,
        accumulator: TelemetryAccumulator,
        state: CodexStreamState,
        context: ParseContext,
    ) {
        val event = parseJsonObject(line.text)
        if (event == null) {
            accumulator.unknownEvent(INVALID)
            return
        }
        accumulator.event()
        when (val type = event.string("type")) {
            "thread.started", "turn.started" -> Unit
            "turn.completed" -> state.completeTurn(event.obj("usage"))
            "turn.failed" -> state.fail(event.obj("error")?.string("message"))
            "error" -> state.noteError(event.string("message"))
            "item.started", "item.updated" -> Unit
            "item.completed" -> item(event.obj("item"), line, accumulator, state, context)
            else -> accumulator.unknownEvent(type ?: MISSING_TYPE)
        }
    }

    private fun item(
        item: JsonObject?,
        line: TimedLine,
        accumulator: TelemetryAccumulator,
        state: CodexStreamState,
        context: ParseContext,
    ) {
        val type = item?.string("type") ?: return
        when (type) {
            "agent_message", "reasoning", "todo_list" -> Unit
            "command_execution" -> command(item, line, accumulator, context)
            "file_change" -> fileChange(item, line, accumulator)
            "error" -> state.noteError(item.string("message"))
            else -> accumulator.toolCall(type)
        }
    }

    private fun command(item: JsonObject, line: TimedLine, accumulator: TelemetryAccumulator, context: ParseContext) {
        accumulator.toolCall("command_execution")
        if ((item.long("exit_code") ?: 0L) != 0L) accumulator.toolError()
        val text = item.string("command").orEmpty()
        val mentioned = context.knownFiles.filter { it in TelemetryAccumulator.slashed(text) }
        mentioned.forEach { accumulator.fileRead(it, line) }
        accumulator.bytesRead(item.string("aggregated_output").orEmpty().toByteArray().size.toLong())
    }

    private fun fileChange(item: JsonObject, line: TimedLine, accumulator: TelemetryAccumulator) {
        accumulator.toolCall("file_change")
        item.items("changes").mapNotNull { it.string("path") }.forEach { accumulator.fileEdited(it, line) }
    }

    private companion object {
        const val INVALID = "invalid-json"
        const val MISSING_TYPE = "missing-type"
    }
}

package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.bool
import io.github.krosstack.kscope.benchmark.agent.items
import io.github.krosstack.kscope.benchmark.agent.obj
import io.github.krosstack.kscope.benchmark.agent.parseJsonObject
import io.github.krosstack.kscope.benchmark.agent.string
import io.github.krosstack.kscope.benchmark.agent.textContent
import kotlinx.serialization.json.JsonObject

internal class ClaudeEventParser : EventParser {
    override fun parse(lines: List<TimedLine>, context: ParseContext): ParsedRun {
        val accumulator = TelemetryAccumulator(context)
        val state = ClaudeStreamState()
        lines.forEach { line -> handle(line, accumulator, state) }
        return state.finish(accumulator)
    }

    private fun handle(line: TimedLine, accumulator: TelemetryAccumulator, state: ClaudeStreamState) {
        val event = parseJsonObject(line.text)
        if (event == null) {
            accumulator.unknownEvent(INVALID)
            return
        }
        accumulator.event()
        when (val type = event.string("type")) {
            "system" -> state.system(event, accumulator)
            "assistant" -> assistant(event, line, accumulator, state)
            "user" -> user(event, accumulator, state)
            "result" -> state.result = event
            else -> accumulator.unknownEvent(type ?: MISSING_TYPE)
        }
    }

    private fun assistant(
        event: JsonObject,
        line: TimedLine,
        accumulator: TelemetryAccumulator,
        state: ClaudeStreamState,
    ) {
        val message = event.obj("message") ?: return
        state.message(message)
        message.items("content").filter { it.string("type") == "tool_use" }.forEach { use ->
            val name = use.string("name") ?: UNKNOWN_TOOL
            accumulator.toolCall(name)
            use.string("id")?.let { state.toolNames[it] = name }
            toolPath(use)?.let { path ->
                when (name) {
                    in READ_TOOLS -> accumulator.fileRead(path, line)
                    in EDIT_TOOLS -> accumulator.fileEdited(path, line)
                }
            }
        }
    }

    private fun user(event: JsonObject, accumulator: TelemetryAccumulator, state: ClaudeStreamState) {
        val content = event.obj("message")?.items("content").orEmpty()
        content.filter { it.string("type") == "tool_result" }.forEach { result ->
            if (result.bool("is_error") == true) accumulator.toolError()
            val tool = result.string("tool_use_id")?.let { state.toolNames[it] }
            if (tool in READ_TOOLS) accumulator.bytesRead(result["content"].textContent().toByteArray().size.toLong())
        }
    }

    private fun toolPath(use: JsonObject): String? {
        val input = use.obj("input") ?: return null
        return input.string("file_path") ?: input.string("notebook_path") ?: input.string("path")
    }

    private companion object {
        val READ_TOOLS = setOf("Read")
        val EDIT_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")
        const val INVALID = "invalid-json"
        const val MISSING_TYPE = "missing-type"
        const val UNKNOWN_TOOL = "unknown"
    }
}

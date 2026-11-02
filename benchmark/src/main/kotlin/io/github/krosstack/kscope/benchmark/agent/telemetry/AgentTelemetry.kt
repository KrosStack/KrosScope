package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.RunFailure
import kotlinx.serialization.Serializable

@Serializable
internal data class Milestone(val eventIndex: Int, val elapsedMs: Long)

@Serializable
internal data class ObservedAgent(
    val version: String? = null,
    val model: String? = null,
    val tools: List<String> = emptyList(),
    val mcpServers: Int = 0,
    val skills: Int = 0,
    val slashCommands: Int = 0,
)

@Serializable
internal data class AgentTelemetry(
    val inputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val costUsd: Double? = null,
    val turns: Int = 0,
    val wallMs: Long = 0,
    val apiMs: Long? = null,
    val toolCalls: Map<String, Int> = emptyMap(),
    val toolErrors: Int = 0,
    val filesRead: List<String> = emptyList(),
    val bytesRead: Long = 0,
    val filesEdited: List<String> = emptyList(),
    val firstGoldRead: Milestone? = null,
    val firstGoldEdit: Milestone? = null,
    val apiRetries: Int = 0,
    val events: Int = 0,
    val unknownEventTypes: Map<String, Int> = emptyMap(),
    val resultSubtype: String? = null,
    val observed: ObservedAgent? = null,
    val warnings: List<String> = emptyList(),
) {
    val uncachedTokens: Long get() = inputTokens + cacheCreationTokens + outputTokens
    val totalTokens: Long get() = uncachedTokens + cacheReadTokens
    val toolCallTotal: Int get() = toolCalls.values.sum()
}

internal data class TimedLine(val index: Int, val elapsedMs: Long, val text: String)

internal class ParseContext(val goldFiles: Set<String>, val knownFiles: Set<String>, val workspaceRoot: String?)

internal sealed interface ParsedRun {
    val telemetry: AgentTelemetry

    data class Finished(override val telemetry: AgentTelemetry) : ParsedRun

    data class Failed(override val telemetry: AgentTelemetry, val failure: RunFailure) : ParsedRun

    data class Unfinished(override val telemetry: AgentTelemetry) : ParsedRun
}

internal interface EventParser {
    fun parse(lines: List<TimedLine>, context: ParseContext): ParsedRun
}

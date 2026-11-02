package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.FailureCode
import io.github.krosstack.kscope.benchmark.agent.RunFailure
import io.github.krosstack.kscope.benchmark.agent.bool
import io.github.krosstack.kscope.benchmark.agent.count
import io.github.krosstack.kscope.benchmark.agent.double
import io.github.krosstack.kscope.benchmark.agent.long
import io.github.krosstack.kscope.benchmark.agent.obj
import io.github.krosstack.kscope.benchmark.agent.string
import io.github.krosstack.kscope.benchmark.agent.strings
import kotlinx.serialization.json.JsonObject

internal class ClaudeStreamState {
    var result: JsonObject? = null
    val toolNames = mutableMapOf<String, String>()
    private var init: JsonObject? = null
    private var retries = 0
    private var authenticationRetries = 0
    private val messages = linkedMapOf<String, JsonObject>()

    fun system(event: JsonObject, accumulator: TelemetryAccumulator) {
        when (val subtype = event.string("subtype")) {
            "init" -> init = event
            "api_retry" -> retry(event)
            else -> accumulator.unknownEvent("system:${subtype ?: "none"}")
        }
    }

    fun message(message: JsonObject) {
        val id = message.string("id") ?: return
        if (message.string("model") == SYNTHETIC_MODEL) return
        message.obj("usage")?.let { messages[id] = it }
    }

    fun finish(accumulator: TelemetryAccumulator): ParsedRun {
        val telemetry = accumulator.build(telemetry())
        val result = result ?: return ParsedRun.Unfinished(withWarnings(telemetry))
        val failure = failure(result)
        return if (failure == null) ParsedRun.Finished(telemetry) else ParsedRun.Failed(telemetry, failure)
    }

    private fun retry(event: JsonObject) {
        retries++
        if (event.string("error") == AUTHENTICATION_ERROR || event.long("error_status") == UNAUTHORIZED) {
            authenticationRetries++
        }
    }

    private fun withWarnings(telemetry: AgentTelemetry): AgentTelemetry {
        val warning = if (authenticationRetries > 0) listOf("authentication retries") else emptyList()
        return telemetry.copy(warnings = telemetry.warnings + warning)
    }

    private fun telemetry(): AgentTelemetry {
        val total = result?.obj("usage")
        val base = if (total != null) fromTotals(total) else fromMessages()
        return base.copy(
            costUsd = result?.double("total_cost_usd"),
            turns = result?.long("num_turns")?.toInt() ?: messages.size,
            apiMs = result?.long("duration_api_ms"),
            apiRetries = retries,
            resultSubtype = result?.string("subtype"),
            observed = init?.let(::observed),
            warnings = init?.let(::environmentWarnings).orEmpty(),
        )
    }

    private fun fromTotals(usage: JsonObject) = AgentTelemetry(
        inputTokens = usage.long("input_tokens") ?: 0,
        cacheCreationTokens = usage.long("cache_creation_input_tokens") ?: 0,
        cacheReadTokens = usage.long("cache_read_input_tokens") ?: 0,
        outputTokens = usage.long("output_tokens") ?: 0,
        reasoningTokens = usage.obj("output_tokens_details")?.long("thinking_tokens") ?: 0,
    )

    private fun fromMessages() = AgentTelemetry(
        inputTokens = messages.values.sumOf { it.long("input_tokens") ?: 0 },
        cacheCreationTokens = messages.values.sumOf { it.long("cache_creation_input_tokens") ?: 0 },
        cacheReadTokens = messages.values.sumOf { it.long("cache_read_input_tokens") ?: 0 },
        outputTokens = messages.values.sumOf { it.long("output_tokens") ?: 0 },
    )

    private fun observed(init: JsonObject) = ObservedAgent(
        version = init.string("claude_code_version"),
        model = init.string("model"),
        tools = init.strings("tools").sorted(),
        mcpServers = init.count("mcp_servers"),
        skills = init.count("skills"),
        slashCommands = init.count("slash_commands"),
    )

    private fun environmentWarnings(init: JsonObject): List<String> = buildList {
        val extra = init.strings("tools").toSet() - ALLOWED_TOOLS
        if (extra.isNotEmpty()) add("unexpected tools: ${extra.sorted().joinToString(",")}")
        if (init.count("mcp_servers") > 0) add("mcp servers present")
        if (init.count("skills") > 0 || init.count("slash_commands") > 0) add("skills or slash commands present")
    }

    private fun failure(result: JsonObject): RunFailure? {
        val subtype = result.string("subtype").orEmpty()
        val isError = result.bool("is_error") == true || subtype.startsWith("error")
        if (!isError) return null
        val status = result.long("api_error_status")
        val text = result.string("result").orEmpty()
        val authentication = status == UNAUTHORIZED || status == FORBIDDEN || AUTHENTICATION_HINT.containsMatchIn(text)
        val firstLine = text.lineSequence().firstOrNull().orEmpty().take(DETAIL_LIMIT)
        val detail = "subtype=$subtype status=${status ?: "-"} $firstLine"
        return RunFailure(if (authentication) FailureCode.AUTHENTICATION else FailureCode.AGENT_ERROR, detail.trim())
    }

    private companion object {
        const val SYNTHETIC_MODEL = "<synthetic>"
        const val AUTHENTICATION_ERROR = "authentication_failed"
        const val UNAUTHORIZED = 401L
        const val FORBIDDEN = 403L
        const val DETAIL_LIMIT = 200
        val AUTHENTICATION_HINT = Regex("authenticat|oauth|/login|not logged in", RegexOption.IGNORE_CASE)
        val ALLOWED_TOOLS = setOf("Read", "Edit", "Write", "Glob", "Grep")
    }
}

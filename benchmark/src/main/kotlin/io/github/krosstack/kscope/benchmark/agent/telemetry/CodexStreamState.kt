package io.github.krosstack.kscope.benchmark.agent.telemetry

import io.github.krosstack.kscope.benchmark.agent.FailureCode
import io.github.krosstack.kscope.benchmark.agent.RunFailure
import io.github.krosstack.kscope.benchmark.agent.long
import kotlinx.serialization.json.JsonObject

internal class CodexStreamState {
    var turnsCompleted = 0
        private set
    var failure: RunFailure? = null
        private set
    private var input = 0L
    private var cached = 0L
    private var output = 0L
    private var reasoning = 0L
    private var lastError: String? = null

    fun completeTurn(usage: JsonObject?) {
        turnsCompleted++
        input += usage?.long("input_tokens") ?: 0
        cached += usage?.long("cached_input_tokens") ?: 0
        output += usage?.long("output_tokens") ?: 0
        reasoning += usage?.long("reasoning_output_tokens") ?: 0
    }

    fun noteError(message: String?) {
        if (message != null) lastError = message
    }

    fun fail(message: String?) {
        val text = message ?: lastError.orEmpty()
        val authentication = AUTHENTICATION_HINT.containsMatchIn(text)
        val code = if (authentication) FailureCode.AUTHENTICATION else FailureCode.AGENT_ERROR
        failure = RunFailure(code, text.lineSequence().firstOrNull().orEmpty().take(DETAIL_LIMIT))
    }

    fun telemetry() = AgentTelemetry(
        inputTokens = (input - cached).coerceAtLeast(0),
        cacheReadTokens = cached,
        outputTokens = output,
        reasoningTokens = reasoning,
        turns = turnsCompleted,
    )

    private companion object {
        const val DETAIL_LIMIT = 200
        val AUTHENTICATION_HINT = Regex("401|unauthori|authenticat|log ?in|api key", RegexOption.IGNORE_CASE)
    }
}

package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.agent.Arm
import io.github.krosstack.kscope.benchmark.agent.launch.AgentAdapter
import io.github.krosstack.kscope.benchmark.agent.launch.ClaudeAdapter
import io.github.krosstack.kscope.benchmark.agent.launch.CodexAdapter
import io.github.krosstack.kscope.benchmark.agent.launch.PromptTransport
import java.time.Duration

internal class AgentSelection(val arms: List<Arm>, val repeats: Int, val only: Set<String>?)

internal object AgentOptionParsing {
    val ARMS_DEFAULT = Arm.entries.joinToString(",") { it.letter }

    fun arms(text: String?): List<Arm>? {
        val letters = (text ?: ARMS_DEFAULT).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val arms = letters.map { Arm.parse(it) }
        val invalid = arms.isEmpty() || arms.any { it == null } || arms.toSet().size != arms.size
        return if (invalid) null else arms.filterNotNull()
    }

    fun adapter(name: String?, model: String?, transport: PromptTransport): AgentAdapter? = when (name) {
        "claude" -> ClaudeAdapter(model ?: ClaudeAdapter.DEFAULT_MODEL, transport)
        "codex" -> CodexAdapter(model, transport)
        else -> null
    }

    fun transport(text: String?): PromptTransport? = when (text) {
        null, "stdin" -> PromptTransport.STDIN
        "argument" -> PromptTransport.ARGUMENT
        else -> null
    }

    fun minutes(text: String?, fallback: Long?): Duration? {
        if (text == null) return fallback?.let(Duration::ofMinutes)
        return text.toLongOrNull()?.takeIf { it > 0 }?.let(Duration::ofMinutes)
    }
}

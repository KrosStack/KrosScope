package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

/**
 * What a translation has produced so far: the calls of the script, in order, and the reasons it is partial. Blocks
 * that are still being filled are written before any other call, so that the order of the plugin is kept.
 */
internal class TranslationOutput {
    private val calls = mutableListOf<ScriptCall>()
    private val reasons = linkedSetOf<String>()
    private val open = ArrayDeque<BlockBuilder>()

    fun translation() = Translation(calls.toList(), reasons.toList())

    fun because(reason: String) {
        reasons += reason
    }

    fun because(more: List<String>) {
        reasons += more
    }

    fun becauseOfBlock(name: String) = because("applies plugins or configures the project in a '$name' block")

    fun becauseNotInlined(functions: List<String>) {
        if (functions.isNotEmpty()) because("calls functions that are not read (${functions.joinToString(", ")})")
    }

    fun emit(call: ScriptCall) {
        open.forEach { it.flush() }
        calls += call
    }

    fun applyPlugin(id: String, range: TextRange) = emit(
        ScriptCall("apply", null, listOf(ScriptArgument("plugin", id, "\"$id\"")), emptyList(), emptyMap(), range),
    )

    fun open(scope: Scope?, at: ScriptCall): BlockBuilder? = scope?.takeIf { it == Scope.ANDROID || it == Scope.KOTLIN }
        ?.let { BlockBuilder(it, at.range, calls).also(open::addLast) }

    fun close(block: BlockBuilder?) {
        block?.flush()
        if (block != null) open.remove(block)
    }
}

package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptCall

/**
 * The statements that configure one extension, collected in the order they run, to be emitted as the
 * `android { }` or `kotlin { }` block of a script. A block that is empty is not emitted.
 */
internal class BlockBuilder(val scope: Scope, private val range: TextRange, private val out: MutableList<ScriptCall>) {
    private val calls = mutableListOf<ScriptCall>()
    private val assignments = linkedMapOf<String, String>()

    fun add(call: ScriptCall) {
        calls += call
    }

    fun assign(entries: Map<String, String>) {
        assignments += entries
    }

    fun flush() {
        if (calls.isEmpty() && assignments.isEmpty()) return
        val name = if (scope == Scope.ANDROID) "android" else "kotlin"
        out += ScriptCall(name, null, emptyList(), calls.toList(), assignments.toMap(), range)
        calls.clear()
        assignments.clear()
    }
}

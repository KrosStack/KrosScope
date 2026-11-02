package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.spi.worker.ScriptCall

private val assignmentOperators = setOf("=", "+=", "-=", "*=", "/=", "%=")
private val projectReceivers = setOf("project", "this")
private val extraNames = listOf("ext", "extra")
private val extraReceivers = extraNames.flatMap { name -> listOf(name) + projectReceivers.map { "$it.$name" } }.toSet()
private const val EXT = "ext"
private const val EXTRA = "extra"
private const val SET = "set"
private val setters = setOf(SET, "setProperty")
private val setterReceivers = extraReceivers + projectReceivers + null
private const val EXTRA_PROPERTIES = "extraProperties"

/**
 * Whether the statements a module's scripts run on the project ([ProjectStatements]) assign a project property or an
 * extra property of a given name: `k = ...` (also with `project.` or `this.`, and with a compound operator), the
 * Groovy call `k '...'`, `ext.k = ...`, `ext['k'] = ...`, `ext { k = ... }` (or `set('k', ...)` or a compound
 * assignment inside it), `ext.set('k', ...)`, `extra.set("k", ...)`, `extensions.extraProperties.set("k", ...)` and
 * `setProperty('k', ...)`, with `ext` or `extra` optionally behind `project.` or `this.`, and the Kotlin
 * `val k by extra(...)`. Assignments inside other blocks do not count, nor do forms the script outline does not carry,
 * such as `extra["k"] = ...` in the Kotlin DSL.
 */
internal object PropertyAssignments {
    fun assign(key: String, sources: List<ScriptSource>): Boolean =
        sources.any { source -> ProjectStatements.of(source.calls).any { isAssigning(it, key) } }

    private fun isAssigning(call: ScriptCall, key: String): Boolean = when {
        call.name in assignmentOperators -> call.receiver?.filterNot(Char::isWhitespace) in targetsOf(key)
        call.name == key -> call.receiver in projectReceivers + null && call.arguments.isNotEmpty()
        else -> isKeptInExtra(call, key)
    }

    private fun isKeptInExtra(call: ScriptCall, key: String): Boolean {
        val named = call.arguments.firstOrNull()?.literal == key
        return when (call.name) {
            in setters -> named && (call.receiver in setterReceivers || isExtraProperties(call.receiver))
            EXTRA -> call.receiver == null && named
            EXT -> call.receiver in projectReceivers + null && isWrittenInBlock(call, key)
            else -> false
        }
    }

    private fun isExtraProperties(receiver: String?): Boolean = receiver?.endsWith(EXTRA_PROPERTIES) == true

    private fun isWrittenInBlock(block: ScriptCall, key: String): Boolean =
        key in block.assignments || block.body.any { call ->
            val named = call.arguments.firstOrNull()?.literal == key
            when (call.name) {
                in assignmentOperators -> call.receiver == key
                in setters -> call.receiver == null && named
                else -> false
            }
        }

    private fun targetsOf(key: String): Set<String> =
        setOf(key) + projectReceivers.map { "$it.$key" } + extraReceivers.flatMap { receiver ->
            listOf("$receiver.$key", "$receiver['$key']", "$receiver[\"$key\"]")
        }
}

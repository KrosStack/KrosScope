package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.script.unquote
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

private val coordinateProperties = setOf("group", "version")
private val publishingBlocks = setOf("publishing", "mavenPublishing")

/**
 * The statements of a plugin class that say what its project publishes, written as the script that does the same:
 * `group = "..."`, `version = "..."` and the `publishing { }` and `mavenPublishing { }` blocks.
 */
internal object PublicationForms {
    /** The statement as it is kept in the translation, or null when it says nothing about publishing. */
    fun of(call: ScriptCall, env: Env): ScriptCall? = assignment(call, env) ?: block(call, env)

    /** The `group` and `version` assignments among the assignments of a block that configures the project. */
    fun assignments(call: ScriptCall): List<ScriptCall> = call.assignments
        .filterKeys { it in coordinateProperties }
        .map { (property, value) -> assignmentOf(property, value, call) }

    private fun assignment(call: ScriptCall, env: Env): ScriptCall? {
        val segments = call.receiver?.split('.').orEmpty()
        val property = segments.lastOrNull().orEmpty()
        val owner = segments.dropLast(1)
        val onProject = when (owner.size) {
            0 -> env.current == Scope.PROJECT
            1 -> env.scopeOf(owner.single()) == Scope.PROJECT
            else -> false
        }
        val isCoordinate = call.name == "=" && property in coordinateProperties
        return call.copy(receiver = property).takeIf { isCoordinate && onProject }
    }

    private fun block(call: ScriptCall, env: Env): ScriptCall? =
        call.takeIf { it.name in publishingBlocks && it.receiver == null && env.current == Scope.PROJECT }

    private fun assignmentOf(property: String, value: String, at: ScriptCall) = ScriptCall(
        name = "=",
        receiver = property,
        arguments = listOf(ScriptArgument(null, unquote(value), value)),
        body = emptyList(),
        assignments = emptyMap(),
        range = at.range,
    )
}

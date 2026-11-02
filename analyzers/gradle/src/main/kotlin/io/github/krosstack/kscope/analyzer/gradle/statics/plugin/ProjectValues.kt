package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

private val compoundOperators = setOf("+=", "-=", "*=", "/=", "%=")
private val setterReceivers = setOf(null, "project", "this")

/**
 * The value of a project property such as `group` or `version`: the last assignment among the statements the scripts
 * of a module run on the project ([ProjectStatements]), in the order Gradle runs them (the top-level statements of
 * every script, convention plugins first, then the `afterEvaluate { }` callbacks in the same order), or, failing
 * that, the `gradle.properties` of its build root. A compound assignment such as `version += '-SNAPSHOT'` makes the
 * value computed. A value that a script reads from a Gradle property by a literal key is resolved as [PropertyReads]
 * does, with the scripts of the module telling which names they assign.
 */
internal class ProjectValues(disk: WorkspaceDisk, rootDirectory: String) {
    private val properties by lazy { GradleProperties.read(disk, rootDirectory) }
    private val reads = PropertyReads(disk, rootDirectory)

    fun of(name: String, sources: List<ScriptSource>, directory: String): Part? {
        val assigned = ProjectStatements.inOrder(sources).mapNotNull { (source, call) ->
            valueOf(call, name)?.let { Part(it.literal, it.expression, source.heuristic) }
        }
        val last = assigned.lastOrNull()?.let { resolved(it, directory, sources) }
        return last ?: properties[name]?.let { Part(it, it, false) }
    }

    /** The part with the value of the property that its expression reads in a module with [sources], if so computed. */
    fun resolved(part: Part, directory: String, sources: List<ScriptSource>): Part {
        val value = part.text ?: reads.valueOf(part.expression, directory, sources)
        return if (value == null) part else Part(value, part.expression, part.heuristic)
    }

    private fun valueOf(call: ScriptCall, name: String): ScriptArgument? = when (call.name) {
        "=" -> call.arguments.firstOrNull()?.takeIf { isTarget(call, name) }
        in compoundOperators -> computed(call).takeIf { isTarget(call, name) }
        "setProperty" -> setValue(call, name)
        name -> call.positional().firstOrNull()?.takeIf { call.receiver in setterReceivers && call.body.isEmpty() }
        else -> null
    }

    private fun isTarget(call: ScriptCall, name: String): Boolean =
        call.receiver in setOf(name, "project.$name", "this.$name")

    private fun setValue(call: ScriptCall, name: String): ScriptArgument? {
        val named = call.receiver in setterReceivers && call.arguments.firstOrNull()?.literal == name
        return call.arguments.getOrNull(1)?.takeIf { named }
    }

    private fun computed(call: ScriptCall): ScriptArgument {
        val operand = call.arguments.joinToString { it.expression }
        return ScriptArgument(null, null, "${call.receiver} ${call.name} $operand")
    }
}

package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.script.unquote
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

private val afterEvaluateReceivers = setOf(null, "project")
private val projectReceivers = setOf(null, "project", "this")
private val compoundOperators = setOf("+=", "-=", "*=", "/=", "%=")

/**
 * The statements the scripts of a module run on its project: the top-level statements, and the bodies of the
 * `afterEvaluate { }` callbacks, which Gradle queues and runs once every script of the project has run, a callback
 * that a callback registers after those already queued. A plain assignment in a callback, which the outline keeps
 * apart from the calls of the callback and by target as written, is listed as an assignment statement after them;
 * when the callback also sets the same property by a call, or assigns it under another spelling such as
 * `project.version` beside `version`, the order is not known, so the assignment is listed with a computed value.
 */
internal object ProjectStatements {
    /** The statements of one script, in no particular order. */
    fun of(calls: List<ScriptCall>): List<ScriptCall> =
        calls.filterNot(::isAfterEvaluate) + calls.filter(::isAfterEvaluate).flatMap { of(callbackStatements(it)) }

    /** The statements of the scripts of a module, each with its script, in the order Gradle runs them. */
    fun inOrder(sources: List<ScriptSource>): List<Pair<ScriptSource, ScriptCall>> =
        generateSequence(sources.map { it to it.calls }) { queued ->
            queued.flatMap { (source, calls) ->
                calls.filter(::isAfterEvaluate).map { source to callbackStatements(it) }
            }.takeIf { it.isNotEmpty() }
        }.flatMap { queued ->
            queued.flatMap { (source, calls) -> calls.filterNot(::isAfterEvaluate).map { source to it } }
        }.toList()

    fun afterEvaluateBodies(calls: List<ScriptCall>): List<List<ScriptCall>> =
        calls.filter(::isAfterEvaluate).map { it.body }

    private fun isAfterEvaluate(call: ScriptCall): Boolean =
        call.name == "afterEvaluate" && call.receiver in afterEvaluateReceivers

    private fun callbackStatements(callback: ScriptCall): List<ScriptCall> {
        val spellings = callback.assignments.keys.groupingBy(::propertyOf).eachCount()
        return callback.body + callback.assignments.map { (target, value) ->
            val property = propertyOf(target)
            val ordered = spellings.getValue(property) == 1 && callback.body.none { setsByCall(it, property) }
            if (ordered) {
                assignment(target, unquote(value), value, callback)
            } else {
                assignment(target, null, "$value or another setting of it in the same callback", callback)
            }
        }
    }

    private fun setsByCall(call: ScriptCall, property: String): Boolean = when (call.name) {
        property -> call.receiver in projectReceivers && call.arguments.isNotEmpty()
        in compoundOperators -> call.receiver?.let(::propertyOf) == property
        "setProperty" -> call.receiver in projectReceivers && call.arguments.firstOrNull()?.literal == property
        else -> false
    }

    private fun propertyOf(target: String): String = target.removePrefix("project.").removePrefix("this.")

    private fun assignment(target: String, literal: String?, value: String, callback: ScriptCall) = ScriptCall(
        name = "=",
        receiver = target,
        arguments = listOf(ScriptArgument(null, literal, value)),
        body = emptyList(),
        assignments = emptyMap(),
        range = callback.range,
    )
}

package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.spi.worker.ScriptCall

private const val THIS = "this"

/**
 * What is known of the names visible in a body: the scope of those that stand for a project or an extension, the
 * text of those that were given a value, and the scope that `this` stands for ([current]; null when it is none).
 */
internal class Env private constructor(
    val current: Scope?,
    private val scopes: Map<String, Scope>,
    private val values: Map<String, String>,
) {
    fun within(scope: Scope?): Env = Env(scope, scopes, values)

    fun valueOf(name: String): String? = values[name]

    fun scopeOf(expression: String): Scope? {
        val text = expression.trim()
        return when {
            text == THIS -> current
            text == "project" -> scopes[text] ?: Scope.PROJECT
            else -> scopes[text] ?: ExtensionScopes.ofType(ExtensionScopes.typeRead(text))
        }
    }

    /**
     * The environment after a `val` call. A name takes the scope of its declared type or of the call that initialises
     * it, and else the scope of the expression that gave it a value, read in [valuesFrom].
     */
    fun declare(binding: ScriptCall, valuesFrom: Env = this): Env {
        val name = binding.arguments.firstOrNull()?.literal
        val value = binding.arguments.getOrNull(1)?.expression
        val scope = ExtensionScopes.ofType(binding.typeArguments.firstOrNull())
            ?: value?.let(valuesFrom::scopeOf)
        return when (name) {
            null -> this
            THIS -> within(scope)
            else -> Env(current, scopes + scopeEntry(name, scope), values + valueEntry(name, value))
        }
    }

    private fun scopeEntry(name: String, scope: Scope?): Map<String, Scope> =
        if (scope == null) emptyMap() else mapOf(name to scope)

    private fun valueEntry(name: String, value: String?): Map<String, String> =
        if (value == null) emptyMap() else mapOf(name to value)

    companion object {
        val none = Env(null, emptyMap(), emptyMap())
    }
}

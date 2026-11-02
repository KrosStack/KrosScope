package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.analyzer.gradle.script.namedArgument
import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** What a statement of a plugin's `apply` does to the plugins of the project. */
internal sealed interface PluginApplication {
    class ById(val id: String) : PluginApplication

    class ByClass(val name: String) : PluginApplication

    /** A plugin whose id or class is computed. */
    data object Computed : PluginApplication

    /** Statements that run only if another plugin is applied. */
    class WhenApplied(val id: String?) : PluginApplication
}

private val pluginManagers = setOf("pluginManager", "plugins")
private val conditionals = setOf("withPlugin", "withId")
private val classLiteral = Regex("""^([\w.]+)::class(?:\.java)?$""")

/** Recognises `apply(plugin = "id")`, `pluginManager.apply("id")`, `plugins.apply(X::class.java)`, `apply<X>()`. */
internal object PluginApplications {
    fun of(call: ScriptCall, env: Env): PluginApplication? {
        val manager = managerOf(call.receiver, env)
        return when {
            manager == null -> null
            call.name == "apply" -> application(call, manager.isNotEmpty())
            call.name in conditionals && manager.isNotEmpty() -> PluginApplication.WhenApplied(call.firstLiteral())
            else -> null
        }
    }

    private fun managerOf(receiver: String?, env: Env): String? {
        val segments = receiver?.split('.').orEmpty().dropWhile { env.scopeOf(it) == Scope.PROJECT }
        return segments.joinToString(".").takeIf { it.isEmpty() || it in pluginManagers }
    }

    private fun application(call: ScriptCall, onManager: Boolean): PluginApplication? {
        val named = call.namedArgument("plugin")
        val positional = call.positional().firstOrNull()
        return when {
            call.typeArguments.size == 1 && call.arguments.isEmpty() ->
                PluginApplication.ByClass(call.typeArguments.single())

            named != null -> named.literal?.let(PluginApplication::ById) ?: PluginApplication.Computed

            onManager && positional != null -> byPositional(positional.literal, positional.expression)

            else -> null
        }
    }

    private fun byPositional(literal: String?, expression: String): PluginApplication {
        val clazz = classLiteral.matchEntire(expression.trim())?.groupValues?.get(1)
        return when {
            literal != null -> PluginApplication.ById(literal)
            clazz != null -> PluginApplication.ByClass(clazz)
            else -> PluginApplication.Computed
        }
    }
}

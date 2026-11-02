package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.worker.PluginClassOutline
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** A plugin class and the source file that declares it. */
internal class PluginClass(val artifact: Artifact, val outline: PluginClassOutline) {
    val simpleName: String = outline.name.substringAfterLast('.').substringAfterLast('$')
}

/** The plugin class a name stands for within one build, by qualified or simple name. */
internal fun interface PluginClassLookup {
    fun find(name: String): PluginClass?
}

/**
 * The statements of a plugin class, written as the script that does the same: `apply(plugin = "id")`,
 * `android { }`, `kotlin { }`, `dependencies { }`. [reasons] say what the plugin does that could not be read.
 */
internal class Translation(val calls: List<ScriptCall>, val reasons: List<String>)

private const val MAX_NESTING = 4

/**
 * Reads what a plugin class contributes to a project from the outline of its `apply` function, without running it.
 * The plugins it applies, the extensions it configures by type (the Android extensions and the Kotlin Multiplatform
 * extension are read, others are not) and the dependencies it declares become the calls of a script. Whatever is
 * computed, conditional or beyond the limits of the outline is left out and named in the reasons of the result.
 */
internal class ClassPluginTranslator(private val classes: PluginClassLookup) {
    fun translate(plugin: PluginClass): Translation {
        val run = Run()
        run.visit(plugin)
        return run.output.translation()
    }

    private inner class Run {
        val output = TranslationOutput()
        private val visiting = mutableListOf<String>()

        fun visit(plugin: PluginClass) {
            visiting += plugin.outline.name
            statements(plugin.outline.apply, Env.none, null)
            output.becauseNotInlined(plugin.outline.notInlined)
            visiting.removeLast()
        }

        private fun statements(calls: List<ScriptCall>, start: Env, block: BlockBuilder?) {
            var env = start
            calls.forEach { env = statement(it, env, block) }
        }

        private fun statement(call: ScriptCall, env: Env, block: BlockBuilder?): Env {
            when {
                call.name == "val" -> return env.declare(call)

                call.inlined -> inlined(call, env, block)

                ScopeForms.isControl(call) ->
                    if (block != null || ScopeForms.isRelevant(call.body)) output.becauseOfBlock(call.name)

                else -> plain(call, env, block)
            }
            return env
        }

        private fun plain(call: ScriptCall, env: Env, block: BlockBuilder?) {
            val application = PluginApplications.of(call, env)
            when {
                application != null -> apply(call, application)
                call.name == "dependencies" && call.receiver == null -> dependencies(call, env)
                else -> scoped(call, env, block)
            }
        }

        private fun apply(call: ScriptCall, application: PluginApplication) {
            when (application) {
                is PluginApplication.ById -> output.applyPlugin(application.id, call.range)

                is PluginApplication.ByClass -> applyClass(application.name)

                PluginApplication.Computed -> output.because("applies a plugin whose id is computed")

                is PluginApplication.WhenApplied -> output.because(
                    "configures the project only if the plugin ${application.id ?: "<computed>"} is applied",
                )
            }
        }

        private fun applyClass(name: String) {
            val other = classes.find(name)
            when {
                other == null -> output.because("applies the plugin class $name, which is not part of the build")

                other.outline.name in visiting || visiting.size >= MAX_NESTING ->
                    output.because("applies the plugin class $name beyond the nesting limit")

                else -> visit(other)
            }
        }

        private fun dependencies(call: ScriptCall, env: Env) {
            val block = DependencyForms.read(call, env)
            output.because(block.reasons)
            if (block.declarations.isNotEmpty()) {
                output.emit(call.copy(body = block.declarations, assignments = emptyMap()))
            }
        }

        private fun inlined(call: ScriptCall, env: Env, block: BlockBuilder?) {
            val bindings = call.body.takeWhile { it.name == "val" }
            val inner = bindings.fold(Env.none) { scoped, binding -> scoped.declare(binding, env) }
            val function = call.copy(body = call.body.drop(bindings.size), assignments = emptyMap())
            enter(inner.current, function, inner, block)
        }

        private fun scoped(call: ScriptCall, env: Env, block: BlockBuilder?) {
            val member = ScopeForms.memberOf(call, env)
            val publication = PublicationForms.of(call, env)
            if (publication != null) return output.emit(publication)
            when (val scope = member?.first ?: ScopeForms.opened(call, env)) {
                Scope.ANDROID, Scope.KOTLIN, Scope.PROJECT ->
                    enter(scope, call, env.within(scope), block, member?.second)

                Scope.OTHER -> Unit

                null -> when {
                    block != null -> ScopeForms.addVerbatim(call, env, block)
                    ScopeForms.isRelevant(call.body) -> output.becauseOfBlock(call.name)
                }
            }
        }

        private fun enter(scope: Scope?, at: ScriptCall, env: Env, enclosing: BlockBuilder?, member: String? = null) {
            val reused = enclosing?.takeIf { it.scope == scope }
            val target = reused ?: output.open(scope, at)
            if (member == null) {
                target?.assign(at.assignments)
                if (scope == Scope.PROJECT) PublicationForms.assignments(at).forEach(output::emit)
                statements(at.body, env, target)
            } else {
                target?.add(at.copy(name = member, receiver = null))
            }
            if (reused == null) output.close(target)
        }
    }
}

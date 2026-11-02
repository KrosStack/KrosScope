package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginReader
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginUse
import io.github.krosstack.kscope.model.Diagnostic

/** The scripts that configure one project, in the order they apply, and the plugins they declare. */
internal class ProjectScripts(
    val sources: List<ScriptSource>,
    val uses: List<PluginUse>,
    val problems: List<Diagnostic>,
)

/**
 * Expands the convention plugins a project applies: each plugin whose id matches a precompiled script plugin or a
 * plugin class contributes its script once, before the script that applies it, so that the project's own script has
 * the last say.
 */
internal class ConventionExpander(private val conventions: Conventions, private val plugins: PluginReader) {
    fun expand(own: ScriptSource): ProjectScripts = Expansion().also { it.visit(own) }.result()

    private inner class Expansion {
        private val visited = mutableSetOf<String>()
        private val sources = mutableListOf<ScriptSource>()
        private val uses = mutableListOf<PluginUse>()
        private val problems = mutableListOf<Diagnostic>()

        fun visit(source: ScriptSource) {
            val reading = plugins.read(source)
            uses += reading.uses
            problems += reading.problems
            reading.uses.filter { it.applied && visited.add(it.id) }.forEach { use -> expandConvention(use.id) }
            sources += source
        }

        fun result() = ProjectScripts(sources, uses, problems)

        private fun expandConvention(id: String) {
            val convention = conventions.conventionFor(id) ?: return
            problems += convention.problems
            convention.source?.let(::visit)
        }
    }
}

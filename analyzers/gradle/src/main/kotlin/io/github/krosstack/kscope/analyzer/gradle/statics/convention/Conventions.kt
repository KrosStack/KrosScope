package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptOutlines
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.analyzer.gradle.statics.StaticDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic

/** The script a plugin id expands to as a convention plugin; [source] is null if it could not be read. */
internal class Convention(val source: ScriptSource?, val problems: List<Diagnostic>)

/**
 * The convention plugins of the included builds: precompiled script plugins, and plugins implemented as classes that
 * `gradlePlugin { }` or a plugin descriptor registers. A class is read from the Kotlin sources of the project that
 * registers it, which are outlined together on first need ([filesToOutline]).
 */
internal class Conventions(
    private val scripts: ConventionScripts,
    private val registrations: Map<String, PluginRegistration>,
    kotlinSources: List<Artifact>,
    private val outlines: ScriptOutlines,
) : PluginClassLookup {
    private val sources = kotlinSources.filter { source ->
        registrations.values.any { RepoPaths.isWithin(source.ref.path.value, sourceRootOf(it.projectDirectory)) }
    }
    private val requested = mutableSetOf<String>()
    private val translator = ClassPluginTranslator(this)
    private val expanded = HashMap<String, Convention?>()

    /** The files that must be outlined before [id] can be expanded; the sources of a project come once. */
    fun filesToOutline(id: String): List<Artifact> = listOfNotNull(scripts.artifactFor(id)) + sourcesToOutline(id)

    private fun sourcesToOutline(id: String): List<Artifact> {
        val project = registrations[id]?.projectDirectory?.takeIf { requested.add(it) } ?: return emptyList()
        return sources.filter { RepoPaths.isWithin(it.ref.path.value, sourceRootOf(project)) }
    }

    /** What [id] expands to, or null if it is no convention plugin of these builds. */
    fun conventionFor(id: String): Convention? = expanded.getOrPut(id) { read(id) }

    override fun find(name: String): PluginClass? {
        val all = loadedClasses()
        return all.firstOrNull { it.outline.name == name }
            ?: all.singleOrNull { '.' !in name && it.simpleName == name }
    }

    private fun read(id: String): Convention? {
        val script = scripts.artifactFor(id)
        val registration = registrations[id]
        val plugin = registration?.let { find(it.implementationClass) }
        return when {
            script != null -> scriptConvention(script, id)
            plugin != null -> classConvention(plugin, id)
            else -> null
        }
    }

    private fun scriptConvention(script: Artifact, id: String): Convention {
        val source = outlines.sourceOf(script, viaConvention = id)
        return Convention(source, listOfNotNull(StaticDiagnostics.scriptNotAnalysed(script).takeIf { source == null }))
    }

    private fun classConvention(plugin: PluginClass, id: String): Convention {
        val translation = translator.translate(plugin)
        val problems = listOfNotNull(ConventionDiagnostics.partiallyAnalysed(plugin, translation.reasons))
        return Convention(ScriptSource(plugin.artifact, translation.calls, viaConvention = id), problems)
    }

    private fun loadedClasses(): List<PluginClass> = sources.flatMap { artifact ->
        outlines.pluginClassesOf(artifact).map { PluginClass(artifact, it) }
    }

    private fun sourceRootOf(project: String): String = RepoPaths.join(project, "src/main")
}

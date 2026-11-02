package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.ArtifactTypes
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.spi.ScriptOutliner
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.PluginClassOutline
import io.github.krosstack.kscope.spi.worker.ScriptCall
import io.github.krosstack.kscope.spi.worker.ScriptOutline

/** A script that was outlined, with the plugin it came from when it was expanded as a convention plugin. */
internal class ScriptSource(val artifact: Artifact, val calls: List<ScriptCall>, val viaConvention: String? = null) {
    val heuristic: Boolean get() = viaConvention != null

    fun location(call: ScriptCall) = SourceLocation(artifact.ref, call.range)
}

/**
 * Outlines the scripts a provider run needs, each once. Scripts in the Kotlin DSL and Kotlin sources go to the Kotlin
 * outliner, in one call so that they can inline each other's functions, and all others to the Groovy outliner; a file
 * an outliner leaves out has no outline.
 */
internal class ScriptOutlines(
    private val kotlin: ScriptOutliner,
    private val groovy: ScriptOutliner,
    private val files: WorkspaceFiles,
) {
    private val outlined = HashMap<ArtifactRef, ScriptOutline?>()

    fun prepare(scripts: Collection<Artifact>) {
        val pending = scripts.filter { it.ref !in outlined }.distinctBy { it.ref }
        val (kotlinFiles, groovyScripts) = pending.partition(::isKotlin)
        outlined += outlineWith(kotlin, kotlinFiles)
        outlined += outlineWith(groovy, groovyScripts)
    }

    fun pluginClassesOf(artifact: Artifact): List<PluginClassOutline> = outlined[artifact.ref]?.pluginClasses.orEmpty()

    fun sourceOf(artifact: Artifact, viaConvention: String? = null): ScriptSource? =
        outlined[artifact.ref]?.let { ScriptSource(artifact, it.calls, viaConvention) }

    private fun outlineWith(outliner: ScriptOutliner, scripts: List<Artifact>): Map<ArtifactRef, ScriptOutline?> {
        if (scripts.isEmpty()) return emptyMap()
        val outlines = outliner.outline(scripts, files)
        return scripts.associate { it.ref to outlines[it.ref] }
    }

    private fun isKotlin(artifact: Artifact) =
        artifact.type.id == ArtifactTypes.KOTLIN_SOURCE || artifact.ref.path.value.endsWith(KOTLIN_DSL_SUFFIX)

    private companion object {
        const val KOTLIN_DSL_SUFFIX = ".kts"
    }
}

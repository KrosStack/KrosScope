package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Module

private const val BUILD_SCRIPT = "gradle.build-script"
private const val KOTLIN_SCRIPT = "kotlin.script"
private const val PRECOMPILED_SUFFIX = ".gradle.kts"

/**
 * The scripts of a build and the modules each one configures: a module's own build script configures that module, and
 * a precompiled convention plugin script configures every module that applies the plugin it defines.
 */
internal class ScriptModules(private val index: GraphIndex) {
    private val modules: List<Module> = index.structure.modules

    fun conventionScripts(): List<Artifact> =
        index.artifacts.filter { it.type.id == KOTLIN_SCRIPT && pluginIdOf(it) != null }

    fun modulesOf(script: Artifact): List<Module> = when (script.type.id) {
        BUILD_SCRIPT -> modules.filter { it.directory.value == script.ref.path.value.substringBeforeLast('/', "") }
        KOTLIN_SCRIPT -> pluginIdOf(script)?.let { id -> modules.filter { id in it.plugins } }.orEmpty()
        else -> emptyList()
    }

    private fun pluginIdOf(script: Artifact): String? =
        script.ref.path.value.substringAfterLast('/').takeIf { it.endsWith(PRECOMPILED_SUFFIX) }
            ?.removeSuffix(PRECOMPILED_SUFFIX)
}

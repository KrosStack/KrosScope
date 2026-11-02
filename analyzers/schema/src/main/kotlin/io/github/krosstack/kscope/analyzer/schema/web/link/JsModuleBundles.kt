package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.graph.GraphReader

private val webPlatforms = setOf(Platform.JS, Platform.WASM)
private val buildDirectories = listOf("build/dist", "build/distributions", "build/kotlin-webpack")

private class JsModule(val id: ModuleId, val repository: String, val directories: List<String>) {
    fun holds(repository: String, path: String): Boolean =
        this.repository == repository && directories.any { RepoPaths.isAtOrInside(path, it) }
}

/**
 * The modules with a Kotlin/JS or Kotlin/Wasm target, by repository. Such a module produces the files of a path that
 * lies at or below one of the directories where its Gradle build puts a bundle, `build/dist`, `build/distributions` or
 * `build/kotlin-webpack`. These are conventions of the Kotlin Gradle plugin that a build can change, so the match is by
 * convention and heuristic.
 */
internal class JsModuleBundles(graph: GraphReader) {
    private val modules: List<JsModule> = graph.structure().let { structure ->
        val repositories = structure.buildRoots.associate { it.id to it.repository.value }
        val webModules = structure.targets.filter { it.platform in webPlatforms }.map { it.module }.toSet()
        structure.modules.filter { it.id in webModules }.mapNotNull { module ->
            repositories[module.buildRoot]?.let { JsModule(module.id, it, directoriesOf(module)) }
        }.sortedBy { it.id.value }
    }

    fun producing(repository: String, path: String): List<BundleProducer> = modules
        .filter { it.holds(repository, path) }
        .map { BundleProducer(NodeRef.OfModule(it.id), EvidenceClass.CONVENTION, Certainty.HEURISTIC) }

    private fun directoriesOf(module: Module): List<String> =
        buildDirectories.mapNotNull { RepoPaths.resolve(module.directory.value, it) }
}

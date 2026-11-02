package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.spi.BuildModel

private const val GENERATED = "build/generated"

/**
 * Records the output directories of code generators that exist below the `build` directory of a module, as generated
 * source roots of the source set they belong to. The generators are SQLDelight, the Compose resources plugin and KSP;
 * a directory that has not been generated yet is not recorded.
 */
internal class GeneratedRoots(private val disk: WorkspaceDisk) {
    fun add(model: BuildModel): BuildModel {
        val modules = model.modules.associateBy { it.id }
        val sourceSets = model.sourceSets.map { sourceSet ->
            val directory = modules[sourceSet.module]?.directory?.value ?: return@map sourceSet
            val generated = candidates(RepoPaths.join(directory, GENERATED), sourceSet.name)
                .filter { (path, _) -> disk.isDirectory(path) }
                .map { (path, kind) -> SourceRoot(RepoPath(path), SourceRootKind(kind), generated = true) }
            val roots = (sourceSet.roots + generated).distinctBy { it.directory.value }
            sourceSet.copy(roots = roots.sortedBy { it.directory.value })
        }
        return model.copy(sourceSets = sourceSets)
    }

    private fun candidates(base: String, sourceSet: String): List<Pair<String, String>> =
        sqldelight(base, sourceSet) + composeResources(base, sourceSet) + ksp(base, sourceSet)

    private fun sqldelight(base: String, sourceSet: String) =
        disk.children("$base/sqldelight/code").map { "$base/sqldelight/code/$it/$sourceSet" to "kotlin" }

    private fun composeResources(base: String, sourceSet: String): List<Pair<String, String>> {
        val generator = "$base/compose/resourceGenerator"
        val sources = disk.children("$generator/kotlin")
            .filter { it.startsWith(sourceSet) || (sourceSet == "commonMain" && it == "commonResClass") }
            .map { "$generator/kotlin/$it" to "kotlin" }
        return sources + ("$generator/assembledResources/$sourceSet" to "resources")
    }

    private fun ksp(base: String, sourceSet: String): List<Pair<String, String>> =
        listOf("$base/ksp/$sourceSet/kotlin" to "kotlin") +
            disk.children("$base/ksp").map { "$base/ksp/$it/$sourceSet/kotlin" to "kotlin" }
}

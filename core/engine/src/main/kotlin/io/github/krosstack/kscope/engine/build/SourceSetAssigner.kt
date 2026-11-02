package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId

/** Gives each artifact the source set whose root is the longest prefix of its path. */
internal class SourceSetAssigner(sourceSets: List<SourceSet>) {
    private val rootIndex: DirectoryIndex<SourceSetId> = DirectoryIndex(
        buildMap {
            sourceSets.sortedBy { it.id.value }.forEach { sourceSet ->
                sourceSet.roots.forEach { root -> putIfAbsent(root.directory.value, sourceSet.id) }
            }
        },
    )

    fun assign(artifacts: List<Artifact>): List<Artifact> =
        artifacts.map { it.copy(sourceSet = rootIndex.nearest(it.ref.path.value, includeSelf = true)) }
}

package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.engine.build.DirectoryIndex
import io.github.krosstack.kscope.engine.discovery.Inventory
import io.github.krosstack.kscope.engine.scope.ScopeFilter
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.OutputRoot
import io.github.krosstack.kscope.spi.OutputRootRequest
import io.github.krosstack.kscope.spi.WorkspaceFiles

/**
 * Adds the files of the output roots that detectors name for the classified files of a repository. Git ignores build
 * output, so discovery does not find these files; they are read from disk when their root exists. Every file below an
 * output root, found by discovery or here, carries the label of its root as [GeneratedInfo.generator], unless it is
 * already marked generated, by the build model or by the scope. The first detector in id order that names a directory
 * decides its generator, and the repository root is no output root.
 */
internal class OutputRootInventory(
    private val detectors: List<ArtifactDetector>,
    private val files: WorkspaceFiles,
    private val directories: RootDirectories,
    private val scope: ScopeFilter,
) {
    fun merge(repository: Repository, inventory: Inventory): Inventory {
        val roots = rootsFor(inventory.artifacts)
        if (roots.isEmpty()) return inventory
        val known = inventory.artifacts.mapTo(HashSet()) { it.ref.path.value }
        val found = directories.read(repository, roots.map { it.directory.value }, known)
        val labels = DirectoryIndex(roots.associate { it.directory.value to it.label })
        val artifacts = (inventory.artifacts + found.artifacts).sortedBy { it.ref.path.value }
            .map { labelled(it, labels) }
        return Inventory(artifacts, inventory.notices + found.notices, inventory.candidates + found.candidates)
    }

    private fun rootsFor(artifacts: List<Artifact>): List<OutputRoot> {
        val request = OutputRootRequest(artifacts, files)
        return detectors.flatMap { it.outputRoots(request) }
            .filter { it.directory.value.isNotEmpty() }
            .distinctBy { it.directory.value }
    }

    private fun labelled(artifact: Artifact, labels: DirectoryIndex<String>): Artifact {
        val path = artifact.ref.path.value
        if (artifact.generated != null || scope.marksGenerated(path)) return artifact
        return labels.nearest(path)?.let { artifact.copy(generated = GeneratedInfo(it, null)) } ?: artifact
    }
}

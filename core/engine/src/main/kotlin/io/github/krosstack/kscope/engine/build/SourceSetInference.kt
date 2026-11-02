package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId

/** Infers the source sets of modules from the artifacts below `src/<source set>/<conventional root>`. */
internal class SourceSetInference(modules: List<Module>) {
    private val moduleIndex = DirectoryIndex(modules.associateBy { it.directory.value })

    fun infer(artifacts: List<Artifact>): List<SourceSet> {
        val rootsBySourceSet = artifacts.mapNotNull(::rootOf).distinct().groupBy({ it.sourceSet }, { it.root })
        return rootsBySourceSet.map { (key, roots) -> sourceSetOf(key, roots.sortedBy { it.directory.value }) }
            .sortedBy { it.id.value }
    }

    private fun rootOf(artifact: Artifact): InferredRoot? {
        val path = artifact.ref.path.value
        val module = moduleIndex.nearest(path)
        val prefix = module?.directory?.value?.takeIf { it.isNotEmpty() }?.let { "$it/" }.orEmpty()
        val segments = path.removePrefix(prefix).split('/')
        val kind = rootKind(segments)
        if (module == null || kind == null) return null
        val directory = prefix + segments.take(ROOT_DEPTH).joinToString("/")
        return InferredRoot(SourceSetKey(module, segments[1]), SourceRoot(RepoPath(directory), kind, generated = false))
    }

    private fun rootKind(segments: List<String>): SourceRootKind? = when {
        segments.size < ROOT_DEPTH || !segments[0].equals(SourceSetConventions.SOURCE_DIRECTORY, true) -> null
        segments.size > ROOT_DEPTH -> SourceSetConventions.directoryRootKind(segments[2])
        SourceSetConventions.isManifestFile(segments[2]) -> SourceSetConventions.manifestKind()
        else -> null
    }

    private fun sourceSetOf(key: SourceSetKey, roots: List<SourceRoot>) = SourceSet(
        id = SourceSetId("${key.module.id.value}@${key.name}"),
        module = key.module.id,
        name = key.name,
        role = SourceSetConventions.roleOf(key.name),
        dependsOn = emptyList(),
        roots = roots,
        targets = emptyList(),
        variants = emptyList(),
        evidenceClass = EvidenceClass.STRUCTURAL,
        certainty = Certainty.HEURISTIC,
    )

    private data class SourceSetKey(val module: Module, val name: String)

    private data class InferredRoot(val sourceSet: SourceSetKey, val root: SourceRoot)

    private companion object {
        const val ROOT_DEPTH = 3
    }
}

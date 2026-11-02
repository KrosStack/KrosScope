package io.github.krosstack.kscope.service.map.tree

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.service.map.wireName
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

/**
 * The upper levels of the tree under [root]: the repositories when there are several, their modules, and the source
 * sets that have files. A file with no source set goes to the module whose directory holds it, and when there is none
 * to the pseudo-module of its repository.
 */
internal class ContainerTree(private val root: TreeNode, private val structure: WorkspaceStructure) {
    private val repositories = repositoryNodes()
    private val repositoryOfRoot = structure.buildRoots.associate { it.id to it.repository }
    val modules: Map<ModuleId, TreeNode> = structure.modules.associate { it.id to moduleNode(it) }
    private val modulesByDirectory = modulesByDirectory()
    private val sourceSets = structure.sourceSets.associateBy { it.id }
    private val platforms = structure.targets.associate { it.id to it.platform }
    private val sourceSetNodes = HashMap<SourceSetId, TreeNode>()
    private val rootFiles = HashMap<RepositoryId, TreeNode>()

    fun containerFor(artifact: Artifact): TreeNode {
        val sourceSet = artifact.sourceSet?.let(sourceSets::get)
        if (sourceSet != null) return sourceSetNode(sourceSet)
        val repository = artifact.ref.repository
        return generateSequence(parentDirectory(artifact.ref.path.value), ::parentDirectory)
            .firstNotNullOfOrNull { modulesByDirectory["${repository.value}|$it"] } ?: rootFilesNode(repository)
    }

    fun sourceSetPlacements(): Map<SourceSetId, TreeNode> = structure.sourceSets.mapNotNull { sourceSet ->
        (sourceSetNodes[sourceSet.id] ?: modules[sourceSet.module])?.let { sourceSet.id to it }
    }.toMap()

    private fun repositoryNodes(): Map<RepositoryId, TreeNode> {
        if (structure.repositories.size < 2) return emptyMap()
        return structure.repositories.associate {
            it.id to root.child("r:${it.id.value}", "repository", it.name)
        }
    }

    private fun repositoryNode(id: RepositoryId): TreeNode = repositories[id] ?: root

    private fun moduleNode(module: Module): TreeNode {
        val repository = repositoryOfRoot[module.buildRoot]
        val parent = repository?.let(::repositoryNode) ?: root
        val traits = module.traits.map { it.wireName() }.sorted().takeIf { it.isNotEmpty() }
        return parent.child("m:${module.id.value}", "module", module.path, NodeFacts(traits = traits))
    }

    private fun modulesByDirectory(): Map<String, TreeNode> = buildMap {
        structure.modules.filter { it.directory.value.isNotEmpty() }.sortedBy { it.id.value }.forEach { module ->
            val repository = repositoryOfRoot[module.buildRoot] ?: return@forEach
            putIfAbsent("${repository.value}|${module.directory.value}", checkNotNull(modules[module.id]))
        }
    }

    private fun sourceSetNode(sourceSet: SourceSet): TreeNode = sourceSetNodes.getOrPut(sourceSet.id) {
        val parent = modules[sourceSet.module] ?: root
        val platform = sourceSet.targets.mapNotNull(platforms::get).distinct().singleOrNull()
        val facts = NodeFacts(platform = platform?.wireName())
        parent.child("s:${sourceSet.id.value}", "source-set", sourceSet.name, facts)
    }

    private fun rootFilesNode(repository: RepositoryId): TreeNode = rootFiles.getOrPut(repository) {
        repositoryNode(repository).child("m:${repository.value}//(root files)", "module", ROOT_FILES)
    }

    private fun parentDirectory(path: String): String? = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }

    private companion object {
        const val ROOT_FILES = "(root files)"
    }
}

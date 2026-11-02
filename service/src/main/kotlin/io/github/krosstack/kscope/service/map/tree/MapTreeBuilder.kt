package io.github.krosstack.kscope.service.map.tree

import io.github.krosstack.kscope.model.WorkspaceLocations

internal class BuiltTree(val nodes: List<TreeNode>, val index: TreeIndex)

/** Builds the tree of the workspace, parents before children and siblings by label. */
internal class MapTreeBuilder(private val facts: MapFacts, private val externals: Collection<String>) {
    fun build(): BuiltTree {
        val root = TreeNode("w:", "workspace", facts.workspace, null)
        val containers = ContainerTree(root, facts.structure)
        val files = FileTree(WorkspaceLocations(facts.structure.repositories), facts.commits)
        facts.artifacts.groupBy(containers::containerFor).forEach { (container, artifacts) ->
            files.attach(container, artifacts)
        }
        val libraries = LibraryTree().attach(root, externals)
        val nodes = buildList { visit(root) }
        val index = TreeIndex(
            files.files.mapValues { it.value.position },
            containers.modules.mapValues { it.value.position },
            containers.sourceSetPlacements().mapValues { it.value.position },
            libraries.mapValues { it.value.position },
        )
        return BuiltTree(nodes, index)
    }

    private fun MutableList<TreeNode>.visit(node: TreeNode) {
        node.position = size
        add(node)
        node.children.sortedWith(ORDER).forEach { visit(it) }
    }

    private companion object {
        val ORDER = compareBy<TreeNode>({ it.label }, { it.id })
    }
}

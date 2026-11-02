package io.github.krosstack.kscope.service.map.tree

internal const val NONE = -1

/** What a node shows about itself and does not count; the counts come when the relationships are placed. */
internal data class NodeFacts(
    val lines: Int = 0,
    val artifactType: String? = null,
    val category: String? = null,
    val generated: Boolean = false,
    val hash: String? = null,
    val commits: Int = 0,
    val path: String? = null,
    val traits: List<String>? = null,
    val platform: String? = null,
)

internal class TreeNode(
    val id: String,
    val type: String,
    val label: String,
    val parent: TreeNode?,
    val facts: NodeFacts = NodeFacts(),
) {
    val children = mutableListOf<TreeNode>()
    var position = NONE
}

internal fun TreeNode.child(id: String, type: String, label: String, facts: NodeFacts = NodeFacts()): TreeNode =
    TreeNode(id, type, label, this, facts).also { children.add(it) }

package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment

internal class FileSlot(val artifact: ArtifactRef, val rank: Int, val unresolvedCount: Int) {
    private val entries = LinkedHashMap<NodeRef, ContextFragment>()
    var rendered: RenderedFile? = null
        private set
    var version = 0
        private set

    val isPresent: Boolean get() = entries.isNotEmpty()

    fun entriesWith(node: NodeRef, fragment: ContextFragment): List<ContextFragment> =
        LinkedHashMap(entries).also { it[node] = fragment }.values.toList()

    fun accept(node: NodeRef, fragment: ContextFragment, result: RenderedFile) {
        entries[node] = fragment
        rendered = result
        version++
    }
}

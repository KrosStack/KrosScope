package io.github.krosstack.kscope.context.selection.legacy

import io.github.krosstack.kscope.context.selection.RenderedFile
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment

internal class LegacyFileSlot(val artifact: ArtifactRef) {
    private val entries = LinkedHashMap<NodeRef, ContextFragment>()
    val proposals = HashMap<NodeRef, RenderedFile>()
    var rendered: RenderedFile? = null
        private set

    val isPresent: Boolean get() = entries.isNotEmpty()

    fun entriesWith(node: NodeRef, fragment: ContextFragment): List<ContextFragment> =
        LinkedHashMap(entries).also { it[node] = fragment }.values.toList()

    fun accept(node: NodeRef, fragment: ContextFragment, result: RenderedFile) {
        entries[node] = fragment
        rendered = result
        proposals.clear()
    }
}

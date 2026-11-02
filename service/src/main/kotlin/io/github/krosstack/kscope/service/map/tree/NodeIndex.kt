package io.github.krosstack.kscope.service.map.tree

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SymbolId

/** The positions of the nodes of the tree that are not symbols. */
internal class TreeIndex(
    private val artifacts: Map<ArtifactRef, Int>,
    private val modules: Map<ModuleId, Int>,
    private val sourceSets: Map<SourceSetId, Int>,
    private val externals: Map<String, Int>,
) {
    fun of(ref: NodeRef): Int = when (ref) {
        is NodeRef.OfArtifact -> artifacts[ref.ref] ?: NONE
        is NodeRef.OfModule -> modules[ref.id] ?: NONE
        is NodeRef.OfSourceSet -> sourceSets[ref.id] ?: NONE
        is NodeRef.OfExternal -> externals[ref.coordinate] ?: NONE
        is NodeRef.OfSymbol -> NONE
    }
}

/** Where each symbol is placed: [positions] finds it in the list of symbols and [nodes] holds its node there. */
internal class SymbolNodes(val positions: Map<String, Int>, val nodes: IntArray) {
    fun of(id: SymbolId): Int = positions[id.value]?.let { nodes[it] } ?: NONE
}

internal class NodeIndex(private val tree: TreeIndex, val symbols: SymbolNodes) {
    fun of(ref: NodeRef): Int = if (ref is NodeRef.OfSymbol) symbols.of(ref.id) else tree.of(ref)
}

package io.github.krosstack.kscope.cli.text.format

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.WorkspaceLocations

/** How a node of the graph is named for a person: a path for a file, a dotted name for a declaration. */
internal object NodeLabels {
    fun of(node: NodeRef, locations: WorkspaceLocations): String = when (node) {
        is NodeRef.OfArtifact -> locations.pathOf(node.ref)
        is NodeRef.OfSymbol -> symbol(node.id)
        is NodeRef.OfModule -> module(node.id)
        is NodeRef.OfSourceSet -> sourceSet(node.id)
        is NodeRef.OfExternal -> node.coordinate
    }

    fun module(id: ModuleId): String = withoutRepository(id.value)

    fun sourceSet(id: SourceSetId): String = withoutRepository(id.value)

    fun symbol(id: SymbolId): String = when (val parsed = id.identity()) {
        is ParsedSymbolId.Valid -> humanDescriptor(parsed.identity.descriptor.render())
        is ParsedSymbolId.Invalid -> id.value
    }

    fun target(relationship: Relationship, locations: WorkspaceLocations): String = when (
        val resolution = relationship.resolution
    ) {
        is Resolution.Resolved -> of(resolution.target, locations)
        is Resolution.Unresolved -> resolution.hint
        is Resolution.NotAnalysed -> resolution.hint
    }

    private fun humanDescriptor(descriptor: String): String =
        descriptor.replace('/', '.').trimEnd('.', '#').replace("#", ".")

    private fun withoutRepository(id: String): String = id.substringAfter("//", id)
}

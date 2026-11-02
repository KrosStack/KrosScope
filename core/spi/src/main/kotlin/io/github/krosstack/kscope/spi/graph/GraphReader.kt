package io.github.krosstack.kscope.spi.graph

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolicReference

/**
 * Read access to the persisted graph. Lists come back in the canonical order of `core/model`. A symbol id owned by
 * several units yields one symbol, preferring a source origin and then the canonically first.
 */
public interface GraphReader : NodeMetrics {
    public fun structure(): WorkspaceStructure

    public fun artifacts(): List<Artifact>

    public fun artifact(ref: ArtifactRef): Artifact?

    public fun symbol(id: SymbolId): Symbol?

    public fun symbols(query: SymbolQuery): List<Symbol>

    /** An empty [kinds] set means all kinds. */
    public fun outgoing(node: NodeRef, kinds: Set<RelationKind> = emptySet()): List<Relationship>

    /** Only resolved relationships have a target, so unresolved ones never appear here. */
    public fun incoming(node: NodeRef, kinds: Set<RelationKind> = emptySet()): List<Relationship>

    public fun relationships(query: RelationshipQuery): List<Relationship>

    /** The references that producers could not look up, of one [namespace] or of all when it is null. */
    public fun symbolicReferences(namespace: String? = null): List<SymbolicReference>

    public fun diagnostics(): List<Diagnostic>

    public fun coverage(): CoverageReport
}

package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

/**
 * Keeps the four bulk reads that retrieval and context both make, so that one command decodes the symbols and
 * relationships of the index once. [invalidate] drops them when the index changes.
 */
internal class MemoizedGraph(val source: GraphReader) : GraphReader by source {
    private var structure: WorkspaceStructure? = null
    private var artifacts: List<Artifact>? = null
    private var symbols: List<Symbol>? = null
    private var relationships: List<Relationship>? = null

    /** Reads the four bulk results now, so that the first query is not the one that waits for them. */
    fun preload() {
        structure()
        artifacts()
        symbols(EVERY_SYMBOL)
        relationships(EVERY_RELATIONSHIP)
    }

    @Synchronized
    fun invalidate() {
        structure = null
        artifacts = null
        symbols = null
        relationships = null
    }

    @Synchronized
    override fun structure(): WorkspaceStructure = structure ?: source.structure().also { structure = it }

    @Synchronized
    override fun artifacts(): List<Artifact> = artifacts ?: source.artifacts().also { artifacts = it }

    @Synchronized
    override fun symbols(query: SymbolQuery): List<Symbol> = if (query == EVERY_SYMBOL) {
        symbols ?: source.symbols(query).also { symbols = it }
    } else {
        source.symbols(query)
    }

    @Synchronized
    override fun relationships(query: RelationshipQuery): List<Relationship> = if (query == EVERY_RELATIONSHIP) {
        relationships ?: source.relationships(query).also { relationships = it }
    } else {
        source.relationships(query)
    }

    private companion object {
        val EVERY_SYMBOL = SymbolQuery(limit = Int.MAX_VALUE)
        val EVERY_RELATIONSHIP = RelationshipQuery(limit = Int.MAX_VALUE)
    }
}

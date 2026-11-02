package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal class SemanticFixture(
    files: List<Artifact>,
    private val declarations: List<Symbol>,
    edges: List<Relationship> = emptyList(),
) : GraphReader by RetrievalFixture(files, edges) {
    override fun symbol(id: SymbolId): Symbol? = declarations.find { it.id == id }

    override fun symbols(query: SymbolQuery): List<Symbol> = declarations.take(query.limit)
}

internal fun retrievalSymbol(
    name: String,
    file: Artifact? = null,
    category: SymbolCategory = SymbolCategory.TYPE,
    container: SymbolId? = null,
    origin: SymbolOrigin = SymbolOrigin.Source,
): Symbol = Symbol(
    id = SymbolId("kscope test module main $name#"),
    kind = SymbolKind("test.symbol", category),
    name = name,
    qualifiedName = name,
    signature = name,
    container = container,
    visibility = Visibility.PUBLIC,
    flags = emptySet(),
    origin = origin,
    declaration = file?.let { SourceLocation(it.ref, TextRange(1, 1, 2, 1)) },
    nameLocation = null,
    documentation = null,
    scope = Scope(),
    evidenceClass = EvidenceClass.STRUCTURAL,
    certainty = Certainty.EXACT,
)

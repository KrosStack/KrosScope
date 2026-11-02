package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal class TestSelectionFixture {
    private val main = sourceSet("main", SourceSetRole.MAIN)
    private val tests = sourceSet("tests", SourceSetRole.UNIT_TEST)
    val subject = artifact("subject").copy(sourceSet = main.id)
    val connected = artifact("connected").copy(sourceSet = tests.id)
    val unrelated = artifact("unrelated").copy(sourceSet = tests.id)
    val declarations = listOf(subject, connected, unrelated).map(::declaration)

    fun node(file: Artifact): NodeRef = NodeRef.OfSymbol(SymbolId(file.ref.path.value))

    fun edit(file: Artifact = subject): RankedFile = RankedFile(file.ref, 1.0, FileRole.EDIT, emptyList())

    fun graph(
        edges: List<Relationship>,
        reversed: Boolean = false,
        extraSymbols: List<Symbol> = emptyList(),
        testRole: SourceSetRole = SourceSetRole.UNIT_TEST,
    ): RetrievalGraph {
        val files = listOf(subject, connected, unrelated)
        val symbols = declarations + extraSymbols
        val base = RetrievalFixture(
            if (reversed) files.reversed() else files,
            if (reversed) edges.reversed() else edges,
            listOf(main, tests.copy(role = testRole)),
        )
        val reader = object : GraphReader by base {
            override fun symbols(query: SymbolQuery) = if (reversed) symbols.reversed() else symbols
            override fun symbol(id: SymbolId) = symbols.find { it.id == id }
        }
        return RetrievalGraph(reader)
    }

    private fun declaration(file: Artifact): Symbol = Symbol(
        SymbolId(file.ref.path.value), SymbolKind("kotlin.class", SymbolCategory.TYPE), file.ref.path.value,
        file.ref.path.value, null, null, null, emptySet(), SymbolOrigin.Source,
        SourceLocation(file.ref, TextRange(1, 1, 2, 1)), null, null, Scope(sourceSet = file.sourceSet),
        EvidenceClass.COMPILER, Certainty.EXACT,
    )

    private fun sourceSet(name: String, role: SourceSetRole): SourceSet = SourceSet(
        SourceSetId(name), ModuleId("shared"), name, role, emptyList(), emptyList(), emptyList(), emptyList(),
        EvidenceClass.BUILD_MODEL, Certainty.EXACT,
    )
}

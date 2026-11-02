package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextInputTest {
    private val source = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
    private val symbol = Symbol(
        SymbolId("declaration"), SymbolKind("kotlin.function", SymbolCategory.CALLABLE), "declaration",
        "declaration", "fun declaration()", null, null, emptySet(), SymbolOrigin.Source,
        SourceLocation(source, TextRange(1, 1, 3, 1)), null, null, Scope(), EvidenceClass.COMPILER, Certainty.EXACT,
    )

    @Test
    fun `ranked declarations retain identity score and edit role in context candidates`() {
        val result = analysis().contextInput()
        val declaration = result.candidates.single { it.node == NodeRef.OfSymbol(symbol.id) }

        assertEquals(0.04, declaration.relevance)
        assertEquals(ContextRole.EDIT, declaration.role)
        assertTrue(result.candidates.any { it.node == NodeRef.OfArtifact(source) })
    }

    @Test
    fun `test scores preserve their ratios while staying on the primary retrieval scale`() {
        val tests = analysis().contextInput().candidates.filter { it.role == ContextRole.TEST }

        assertEquals(listOf(0.05, 0.0125), tests.map { it.relevance })
        assertTrue(tests.all { it.relevance <= 0.05 })
    }

    @Test
    fun `symbols outside the edit set retain read roles`() {
        val result = analysis().copy(files = emptyList()).contextInput()

        assertEquals(ContextRole.READ, result.candidates.single { it.node == NodeRef.OfSymbol(symbol.id) }.role)
        assertEquals(0.04, result.candidates.filter { it.role == ContextRole.TEST }.maxOf { it.relevance })
    }

    private fun analysis(): AnalysisResult = AnalysisResult(
        AnalyzedQuery("change", listOf("change"), emptyList(), emptyList(), emptyList()),
        listOf(RankedFile(source, 0.05, FileRole.EDIT, emptyList())),
        listOf(RankedSymbol(symbol, 0.04)), emptyList(),
        listOf(
            RankedFile(source.copy(path = RepoPath("test-a")), 4.0, FileRole.READ, emptyList()),
            RankedFile(source.copy(path = RepoPath("test-b")), 1.0, FileRole.READ, emptyList()),
        ),
        emptyList(), ChangeScope(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false),
        emptyList(), emptyList(), emptyMap(), "limited",
    )
}

package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
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
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationSymbolMetricsTest {
    private val structure = WorkspaceStructure(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    @Test
    fun `declared gold kind aliases receive the same symbol credit as their canonical kinds`() {
        val aliases = mapOf(
            "sq.table" to "sqldelight.table",
            "sq.query" to "sqldelight.query",
            "manifest.receiver" to "android.manifest.receiver",
            "manifest.activity" to "android.manifest.activity",
            "android.layout" to "android.resource.layout",
            "android.navigation" to "android.resource.navigation",
            "catalog.version" to "gradle.catalog.version",
            "catalog.library" to "gradle.catalog.library",
        )

        aliases.forEach { (alias, canonical) ->
            val symbol = symbol(canonical)
            val canonicalAddress = address(symbol)
            val aliasedAddress = canonicalAddress.copy(kind = alias)

            assertEquals(1.0, score(symbol, canonicalAddress), canonical)
            assertEquals(1.0, score(symbol, aliasedAddress), alias)
        }
    }

    @Test
    fun `canonical aliases preserve strict kind name file and supplied declaration line criteria`() {
        val symbol = symbol("sqldelight.table")
        val expected = address(symbol).copy(kind = "sq.table")
        val rejected = listOf(
            expected.copy(kind = "sq.query"),
            expected.copy(kind = "manifest.receiver"),
            expected.copy(name = "Different"),
            expected.copy(fqName = "sample.Different"),
            expected.copy(file = "different"),
            expected.copy(line = 8),
        )

        rejected.forEach { assertEquals(0.0, score(symbol, it), it.toString()) }
    }

    private fun score(symbol: Symbol, address: LocalizationSymbolAddress): Double? {
        val task = LocalizationTask("sample", "change declaration", listOf("source"), symbols = listOf(address))
        val result = AnalysisResult(
            query = AnalyzedQuery("change", emptyList(), emptyList(), emptyList(), emptyList()),
            files = emptyList(),
            symbols = listOf(RankedSymbol(symbol, 1.0)),
            modules = emptyList(),
            tests = emptyList(),
            boundaries = emptyList(),
            scope = ChangeScope(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false),
            risks = emptyList(),
            unresolved = emptyList(),
            evidence = emptyMap(),
            confidence = "none",
        )
        return LocalizationAnalysisMetrics.score(result, task, structure).symbolRecall10
    }

    private fun address(symbol: Symbol) = LocalizationSymbolAddress(
        file = "source",
        fqName = symbol.qualifiedName,
        name = symbol.name,
        kind = symbol.kind.id,
        line = 7,
    )

    private fun symbol(kind: String): Symbol {
        val artifact = ArtifactRef(RepositoryId("sample"), RepoPath("source"))
        val location = SourceLocation(artifact, TextRange(7, 1, 7, 6))
        return Symbol(
            id = SymbolId("kscope test module main sample/Entry#"),
            kind = SymbolKind(kind, SymbolCategory.SCHEMA),
            name = "Entry",
            qualifiedName = "sample.Entry",
            signature = null,
            container = null,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = location,
            nameLocation = location,
            documentation = null,
            scope = Scope(),
            evidenceClass = EvidenceClass.STRUCTURAL,
            certainty = Certainty.EXACT,
        )
    }
}

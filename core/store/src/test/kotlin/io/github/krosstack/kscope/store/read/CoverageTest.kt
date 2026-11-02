package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.spi.graph.CoverageCell
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageTest {
    private val relationships = listOf(
        Builders.edge("a", Resolutions.resolved("b")),
        Builders.edge("c", Resolutions.resolved("b")),
        Builders.edge("a", Resolutions.resolved("b", Certainty.HEURISTIC), evidence = EvidenceClass.STRUCTURAL),
        Builders.edge("a", Resolutions.unresolved()),
        Builders.edge("a", Resolutions.notAnalysed(), RelationKind.IMPORTS, EvidenceClass.TEXTUAL),
        Builders.edge("a", Resolutions.resolved("b"), RelationKind.EXTENDS),
    )

    @Test
    fun `coverage counts relationships by kind evidence class and resolution state`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.edgeBatch("unit", relationships))
            val expected = listOf(
                CoverageCell(RelationKind.CALLS, EvidenceClass.COMPILER, "exact", 2),
                CoverageCell(RelationKind.CALLS, EvidenceClass.COMPILER, "unresolved", 1),
                CoverageCell(RelationKind.CALLS, EvidenceClass.STRUCTURAL, "heuristic", 1),
                CoverageCell(RelationKind.EXTENDS, EvidenceClass.COMPILER, "exact", 1),
                CoverageCell(RelationKind.IMPORTS, EvidenceClass.TEXTUAL, "not-analysed", 1),
            )
            assertEquals(expected, store.coverage().cells)
        }
    }

    @Test
    fun `coverage counts artifacts by type and symbols by kind`() {
        val facts = artifacts().map { Fact.OfArtifact(it) } + symbols().map { Fact.OfSymbol(it) }
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.batch("unit", facts))
            val report = store.coverage()
            assertEquals(mapOf("java.source" to 1L, "kotlin.source" to 2L), report.artifactsByType)
            assertEquals(mapOf("kotlin.function" to 2L, "kotlin.property" to 1L), report.symbolsByKind)
        }
    }

    @Test
    fun `a symbol owned by two units is counted once`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.symbols("unit-a", Builders.symbol("shared")))
            store.replace(Builders.symbols("unit-b", Builders.symbol("shared")))
            assertEquals(mapOf("kotlin.function" to 1L), store.coverage().symbolsByKind)
        }
    }

    @Test
    fun `an empty store has empty coverage`() {
        SqliteGraphStore.inMemory().use { store ->
            assertEquals(CoverageReport(emptyList(), emptyMap(), emptyMap()), store.coverage())
        }
    }

    private fun artifacts(): List<Artifact> {
        val javaType = ArtifactType("java.source", ArtifactCategory.SOURCE)
        val kotlinFiles = listOf("a.kt", "b.kt").map { Samples.artifact.copy(ref = Builders.artifactRef(it)) }
        val javaFile = Samples.artifact.copy(ref = Builders.artifactRef("C.java"), type = javaType)
        return (kotlinFiles + javaFile).map { it.copy(generated = null) }
    }

    private fun symbols() = listOf(
        Builders.symbol("one"),
        Builders.symbol("two"),
        Builders.symbol("three").copy(kind = SymbolKind("kotlin.property", SymbolCategory.VARIABLE)),
    )
}

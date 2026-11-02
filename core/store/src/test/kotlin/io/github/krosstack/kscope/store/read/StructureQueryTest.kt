package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StructureQueryTest {
    @Test
    fun `the structure lists every structural fact`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Samples.batch)
            val expected = WorkspaceStructure(
                repositories = listOf(Samples.repository),
                buildRoots = listOf(Samples.buildRoot),
                modules = listOf(Samples.module),
                targets = listOf(Samples.target),
                variants = listOf(Samples.variant),
                sourceSets = listOf(Samples.sourceSet),
            )
            assertEquals(expected, store.structure())
        }
    }

    @Test
    fun `the structure of an empty store is empty`() {
        SqliteGraphStore.inMemory().use { store ->
            val empty = WorkspaceStructure(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
            assertEquals(empty, store.structure())
        }
    }

    @Test
    fun `modules are ordered canonically and one module is returned per id`() {
        val one = Samples.module.copy(id = ModuleId("r//:one"))
        val two = Samples.module.copy(id = ModuleId("r//:two"))
        val twoAgain = two.copy(path = ":elsewhere")
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.batch("unit-1", listOf(Fact.OfModule(two), Fact.OfModule(one))))
            store.replace(Builders.batch("unit-2", listOf(Fact.OfModule(twoAgain))))
            assertEquals(listOf(one, two), store.structure().modules)
        }
    }

    @Test
    fun `an artifact is found by its reference`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Samples.batch)
            assertEquals(Samples.artifact, store.artifact(Samples.artifactRef))
            assertEquals(listOf(Samples.artifact), store.artifacts())
            assertNull(store.artifact(Builders.artifactRef("src/missing.kt")))
        }
    }

    @Test
    fun `artifacts are listed in canonical order`() {
        val artifacts = listOf("src/c.kt", "src/a.kt", "src/b.kt").map {
            Samples.artifact.copy(ref = Builders.artifactRef(it), generated = null)
        }
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.batch("unit", artifacts.map { Fact.OfArtifact(it) }))
            assertEquals(artifacts.sortedBy { it.ref.path.value }, store.artifacts())
        }
    }

    @Test
    fun `diagnostics from all units are listed in canonical order`() {
        val later = Diagnostic(DiagnosticSeverity.ERROR, ReasonCode("b.code"), "b", null)
        val earlier = Diagnostic(DiagnosticSeverity.INFO, ReasonCode("a.code"), "a", Samples.location)
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.batch("unit-1", listOf(Fact.OfDiagnostic(later))))
            store.replace(Builders.batch("unit-2", listOf(Fact.OfDiagnostic(earlier))))
            val expected = CanonicalFacts.sorted(
                Builders.batch("x", listOf(Fact.OfDiagnostic(later), Fact.OfDiagnostic(earlier))),
            ).facts.map { (it as Fact.OfDiagnostic).value }
            assertEquals(expected, store.diagnostics())
        }
    }
}

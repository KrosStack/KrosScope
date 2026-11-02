package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Samples
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCountsTest {
    @Test
    fun `an empty store counts nothing`() {
        SqliteGraphStore.inMemory().use { store ->
            assertEquals(StoreCounts(0, 0, 0, 0, 0, 0, 0), (store as SqliteGraphStore).counts())
        }
    }

    @Test
    fun `the counts equal the sizes of the structure and of the diagnostics that the readers decode`() {
        val twice = Samples.module.copy(path = ":elsewhere")
        val other = Samples.module.copy(id = ModuleId("r//:other"))
        val diagnostic = Diagnostic(DiagnosticSeverity.INFO, ReasonCode("a.code"), "a", null)
        SqliteGraphStore.inMemory().use { generic ->
            val store = generic as SqliteGraphStore
            store.replace(Samples.batch)
            store.replace(Builders.batch("unit-1", listOf(Fact.OfModule(other), Fact.OfDiagnostic(diagnostic))))
            store.replace(Builders.batch("unit-2", listOf(Fact.OfModule(twice), Fact.OfDiagnostic(diagnostic))))

            val structure = store.structure()
            val counts = store.counts()

            assertEquals(structure.repositories.size, counts.repositories)
            assertEquals(structure.buildRoots.size, counts.buildRoots)
            assertEquals(structure.modules.size, counts.modules)
            assertEquals(structure.sourceSets.size, counts.sourceSets)
            assertEquals(structure.targets.size, counts.targets)
            assertEquals(structure.variants.size, counts.variants)
            assertEquals(store.diagnostics().size, counts.diagnostics)
            assertEquals(2, counts.modules, "a module that two units declare counts once")
        }
    }
}

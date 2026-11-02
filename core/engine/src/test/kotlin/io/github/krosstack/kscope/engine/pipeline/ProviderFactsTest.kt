package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.canonicalDump
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.spi.graph.GraphStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderFactsTest {
    private val provider = AdditionalFactsProvider()
    private val registry = coreRegistry(providers = listOf(provider))

    private fun GraphStore.providerFacts(): List<Fact> = batches().first { it.producer == provider.producer }.facts

    @Test
    fun `facts a provider adds are stored in its build model unit`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                val facts = engine.store.providerFacts()
                val relationships = facts.filterIsInstance<Fact.OfRelationship>()
                assertEquals(1, facts.count { it is Fact.OfSymbol })
                assertEquals(listOf(RelationKind.REFERENCES), relationships.map { it.value.kind })
            }
        }
    }

    @Test
    fun `an unchanged model is read back without the additional facts becoming dependencies`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                val before = engine.canonicalDump()
                val second = engine.update()
                assertEquals(1, provider.calls)
                assertEquals(0, second.unitsProduced)
                assertEquals(before, engine.canonicalDump())
                assertTrue(engine.store.providerFacts().any { it is Fact.OfSymbol })
            }
        }
    }

    @Test
    fun `a changed build script asks the provider again and the incremental result matches a rebuild`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                workspace.write("core/build.gradle.kts", "// changed\n")
                engine.update()
                assertEquals(2, provider.calls)
                engine.assertMatchesRebuild()
            }
        }
    }
}

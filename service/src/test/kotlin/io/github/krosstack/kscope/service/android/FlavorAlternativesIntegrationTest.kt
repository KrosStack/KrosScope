package io.github.krosstack.kscope.service.android

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.analysis.edges
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scans an Android application with a demo and a full flavor that each implement one shared interface. */
class FlavorAlternativesIntegrationTest {
    private val sample = SampleWorkspace("flavors")
    private val flagsName = "sample.flavors.flavor.FlavorFlags"
    private val appFile = "app/src/main/kotlin/sample/flavors/App.kt"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `the implementations in two flavors are alternatives, bound once and by the build model`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val graph = service.graph()
            val implementations = graph.symbols(SymbolQuery(qualifiedName = flagsName))
            assertEquals(2, implementations.size, "One object in each flavor")

            val alternatives = graph.edges(RelationKind.BINDS, "variant-alternative")
            val between = alternatives.single { it.from == NodeRef.OfSymbol(implementations.first().id) }

            val target = NodeRef.OfSymbol(implementations.last().id)
            assertEquals(Resolution.Resolved(target, Certainty.HEURISTIC), between.resolution, "A static model")
            assertEquals(EvidenceClass.BUILD_MODEL, between.evidenceClass)
            assertTrue(alternatives.size >= 2, "The member is bound as well: $alternatives")
        }
    }

    @Test
    fun `the compiler reaches one implementation from the shared usage site`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val implementations = graph.symbols(SymbolQuery(qualifiedName = flagsName)).map { NodeRef.OfSymbol(it.id) }

            val usage = graph.edges(RelationKind.REFERENCES).filter { it.site?.artifact?.path?.value == appFile }
            val reached = usage.mapNotNull { (it.resolution as? Resolution.Resolved)?.target }

            assertEquals(1, reached.count { it in implementations }, "$reached")
        }
    }
}

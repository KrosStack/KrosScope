package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.NotIndexedException
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.declarationsNamed
import io.github.krosstack.kscope.service.neighbourhood
import io.github.krosstack.kscope.service.resolveNode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every lookup that reads a few rows must give what [ReferenceLookups] gives by reading the whole graph. */
class PointLookupIdentityTest {
    private val sample = SampleWorkspace("tests")

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `resolving a node by indexed lookups gives the candidates that scanning every symbol gives`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val all = ReferenceLookups.queries(service.graph())
            assertTrue(all.size > 40, "the sample should give many queries, got ${all.size}")
            all.forEach { query ->
                val expected = ReferenceLookups.resolution(service.graph(), query)
                assertEquals(expected, service.resolveNode(query).candidates, query)
            }
        }
    }

    @Test
    fun `the impact found from the rows around a node equals the impact found from the whole graph`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val whole = TaskRetrieval(graph, service.searchIndex())
            ReferenceLookups.queries(graph).forEach { query ->
                assertEquals(ServiceJson.encode(whole.impact(query)), ServiceJson.encode(service.impact(query)), query)
            }
        }
    }

    @Test
    fun `the neighbourhood read by indexed lookups equals the one read from every symbol`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val symbols = ReferenceLookups.everySymbol(graph).map { it.id.value }
            val nodes = symbols + graph.artifacts().map { it.ref.path.value } + listOf("Nothing_Here")
            nodes.forEach { node ->
                val expected = ReferenceLookups.neighbourhood(graph, node)
                val actual = service.neighbourhood(node)
                assertEquals(expected.symbol, actual.symbol, node)
                assertEquals(expected.declared, actual.declared, node)
                assertEquals(expected.dependsOn, actual.dependsOn, node)
                assertEquals(expected.referredBy, actual.referredBy, node)
            }
        }
    }

    @Test
    fun `the declarations named by a text are the ones symbol lists first`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val greeters = service.declarationsNamed("GreeterTest")
            assertTrue(greeters.size >= 2, "the sample declares GreeterTest in two modules")
            val ranked = service.symbol("GreeterTest").symbols.take(greeters.size).map { it.symbol.id }.toSet()
            assertEquals(greeters.map { it.id }.toSet(), ranked)
        }
    }

    @Test
    fun `a question that reaches more nodes than the limit gives up and leaves the caller to read the index`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val closure = ImpactClosure(service.graph(), service.searchIndex(), limit = 1)

            assertNull(closure.analyze("sample.tests.Greeter"))
            assertNotNull(ImpactClosure(service.graph(), service.searchIndex()).analyze("sample.tests.Greeter"))
        }
    }

    @Test
    fun `info counts the structure and the diagnostics the way the full lists would`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val info = service.info()
            val structure = graph.structure()

            assertEquals(structure.modules.size, info.structure.modules)
            assertEquals(structure.sourceSets.size, info.structure.sourceSets)
            assertEquals(structure.repositories.size, info.structure.repositories)
            assertEquals(structure.buildRoots.size, info.structure.buildRoots)
            assertEquals(structure.targets.size, info.structure.targets)
            assertEquals(structure.variants.size, info.structure.variants)
            assertEquals(graph.diagnostics().size, info.diagnostics)
        }
    }

    @Test
    fun `lookups on a workspace that was never scanned fail without creating an index`() {
        KrosScope.open(sample.root).use { service ->
            kotlin.test.assertFailsWith<NotIndexedException> { service.resolveNode("Greeter") }
            kotlin.test.assertFailsWith<NotIndexedException> { service.impact("Greeter") }
            kotlin.test.assertFailsWith<NotIndexedException> { service.neighbourhood("Greeter") }
            assertTrue(!java.nio.file.Files.exists(sample.root.resolve(".kscope")))
        }
    }
}

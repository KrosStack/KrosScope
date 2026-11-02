package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scans two JVM modules with tests that are named after their subject and tests that are not. */
class TestSubjectsIntegrationTest {
    private val sample = SampleWorkspace("tests")

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `a test is bound to the class it is named after, exactly when the compiler agrees`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val graph = service.graph()

            val named = assertNotNull(graph.testedBy("sample.tests.GreeterTest"))
            assertEquals("sample.tests.Greeter", graph.subjectOf(named))
            assertEquals(Certainty.EXACT, (named.resolution as Resolution.Resolved).certainty)
            assertEquals(EvidenceClass.COMPILER, named.evidenceClass)

            val unconfirmed = assertNotNull(graph.testedBy("sample.tests.FormatterSpec"))
            assertEquals("sample.tests.Formatter", graph.subjectOf(unconfirmed))
            assertEquals(Certainty.PROBABLE, (unconfirmed.resolution as Resolution.Resolved).certainty)
            assertEquals(EvidenceClass.CONVENTION, unconfirmed.evidenceClass)
        }
    }

    @Test
    fun `a test with no matching name is bound to the class it uses most, unless two tie`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()

            val scenario = assertNotNull(graph.testedBy("sample.tests.ScenarioTest"))
            assertEquals("sample.tests.Greeter", graph.subjectOf(scenario))
            assertEquals(Certainty.HEURISTIC, (scenario.resolution as Resolution.Resolved).certainty)

            assertNull(graph.testedBy("sample.tests.TieTest"))
            assertNull(graph.testedBy("sample.tests.OrphanTest"))
        }
    }

    @Test
    fun `a test of one module is bound to the class of a module it depends on`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()

            val inOther = graph.symbols(SymbolQuery(qualifiedName = "sample.other.GreeterTest")).single()
            val edge = graph.edges(RelationKind.TESTS).single { it.from == NodeRef.OfSymbol(inOther.id) }

            assertEquals("sample.tests.Greeter", graph.subjectOf(edge))
            assertEquals("sample.other.Report", graph.subjectOf(graph.testedBy("sample.other.ReportTest")))
        }
    }

    @Test
    fun `a second scan is verified against a clean rebuild with the linkers in place`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val orphan = "package sample.tests\n\nimport org.junit.Test\n\nclass OrphanTest {\n    @Test\n" +
                "    fun uses() {\n        Formatter().pad(\"x\")\n    }\n}\n"
            sample.write("core/src/test/kotlin/sample/tests/OrphanTest.kt", orphan)

            val verified = assertNotNull(service.scan(verify = true).verification)

            assertTrue(verified.matches, "${verified.differences}")
            val graph = service.graph()
            assertEquals("sample.tests.Formatter", graph.subjectOf(graph.testedBy("sample.tests.OrphanTest")))
        }
    }
}

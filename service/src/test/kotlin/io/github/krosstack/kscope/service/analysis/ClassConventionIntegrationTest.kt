package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scans a workspace whose convention plugin is a class of an included build, with the real analyzer worker for both
 * the build model and the sources: the module that applies it gets the plugin and the project dependency that the
 * class declares, and a reference from the module into the other one is resolved through that dependency.
 */
class ClassConventionIntegrationTest {
    private val sample = SampleWorkspace("classconv")
    private val feature = "feature/src/main/kotlin/classconv/feature/Feature.kt"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun GraphReader.dependencies() =
        relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), limit = Int.MAX_VALUE))

    @Test
    fun `the module that applies a plugin class gets what the class applies and declares`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val graph = service.graph()
            val module = graph.structure().modules.single { it.path == ":feature" }
            assertEquals(listOf("classconv.feature", "org.jetbrains.kotlin.jvm"), module.plugins)
            assertEquals(Certainty.HEURISTIC, module.certainty)
            val edge = graph.dependencies().single { (it.from as? NodeRef.OfModule)?.id == module.id }
            assertEquals("implementation", edge.attributes["configuration"])
            val target = (edge.resolution as Resolution.Resolved).target as NodeRef.OfModule
            assertTrue(target.id.value.endsWith("//:core"), target.id.value)
        }
    }

    @Test
    fun `a reference into the module the class depends on is resolved`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val targets = graph.relationships(RelationshipQuery(limit = Int.MAX_VALUE))
                .filter { it.site?.artifact?.path?.value == feature }
                .mapNotNull { (it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol }
                .mapNotNull { graph.symbol(it.id)?.qualifiedName }
            assertTrue("classconv.core.Greeter" in targets, "$targets")
        }
    }
}

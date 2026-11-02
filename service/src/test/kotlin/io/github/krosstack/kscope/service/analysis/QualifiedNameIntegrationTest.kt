package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scans a workspace whose application imports a class of a module that its build script does not declare. */
class QualifiedNameIntegrationTest {
    private val sample = SampleWorkspace("missing")
    private val main = "app/src/main/kotlin/sample/app/Main.kt"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `an import of a class of an undeclared dependency gets a heuristic edge next to the unresolved one`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val imports = graph.edges(RelationKind.IMPORTS).filter { it.site?.artifact?.path?.value == main }

            val matched = imports.single { it.subKind == "qualified-name-match" }
            val target = (matched.resolution as Resolution.Resolved).target as NodeRef.OfSymbol
            assertEquals("sample.lib.Helper", graph.symbol(target.id)?.qualifiedName)
            assertEquals(Certainty.HEURISTIC, (matched.resolution as Resolution.Resolved).certainty)
            assertEquals(EvidenceClass.TEXTUAL, matched.evidenceClass)

            val unresolved = imports.map { it.resolution }.filterIsInstance<Resolution.Unresolved>()
            assertEquals(
                listOf("androidx.compose.runtime.Composable", "sample.lib.Helper"),
                unresolved.map { it.hint }.sorted(),
            )
        }
    }

    @Test
    fun `coverage tells the matched import from the unresolved one by evidence and state`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val cells = service.info().coverage.filter { it.relation == "imports" }

            assertTrue(cells.any { it.evidence == "textual" && it.state == "heuristic" && it.count == 1L }, "$cells")
            assertTrue(cells.any { it.evidence == "compiler" && it.state == "unresolved" && it.count >= 1L }, "$cells")
        }
    }
}

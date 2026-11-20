package io.github.krosstack.kscope.service.buildmodel

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NestedRootScanTest {
    private val sample = SampleWorkspace("nested-roots")

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `independent nested builds that include their roots record each declaration once`() {
        KrosScope.open(sample.root, OpenOptions(level = ScanLevel.RESOLVED)).use { service ->
            val scan = service.scan(rebuild = true)
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val symbols = service.graph().symbols(SymbolQuery(qualifiedName = "example.counter.Counter"))
            assertEquals(
                listOf(
                    "plugin/src/test/fixture/clean/src/main/kotlin/example/counter/schema.kt",
                    "plugin/src/test/fixture/dirty/src/main/kotlin/example/counter/schema.kt",
                ),
                symbols.map { it.declaration?.artifact?.path?.value }.sortedBy { it },
            )
            assertEquals(2, symbols.map { it.id }.distinct().size)
        }
    }
}

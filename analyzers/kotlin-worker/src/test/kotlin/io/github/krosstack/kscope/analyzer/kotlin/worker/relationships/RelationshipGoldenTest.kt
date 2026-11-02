package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test

class RelationshipGoldenTest {
    private fun golden(name: String, modules: List<WorkerModule>, synthesized: (SymbolId) -> Boolean = { false }) =
        Golden.assertMatches("relationships-$name.jsonl", Golden.relationships(analyzeAll(modules, synthesized)))

    @Test
    fun `references between Kotlin declarations of one source set match the golden output`() =
        golden("lang", listOf(RelationSamples.lang))

    @Test
    fun `references across KMP source sets and modules match the golden output`() =
        golden("kmp", RelationSamples.multiplatform)

    @Test
    fun `references between Kotlin and Java in one source set match the golden output`() =
        golden("mixed", listOf(RelationSamples.mixed))

    @Test
    fun `inheritance and overrides across modules match the golden output`() =
        golden("inheritance", RelationSamples.inheritance)

    @Test
    fun `two source sets of one module referring to each other match the golden output`() =
        golden("merged", listOf(RelationSamples.merged))

    @Test
    fun `references to synthetic stubs match the golden output`() =
        golden("stubs", listOf(RelationSamples.withStubs), RelationSamples::isStubSymbol)
}

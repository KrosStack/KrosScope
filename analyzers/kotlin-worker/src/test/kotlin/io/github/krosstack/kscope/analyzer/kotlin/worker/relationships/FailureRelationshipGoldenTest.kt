package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test

class FailureRelationshipGoldenTest {
    private fun golden(name: String, modules: List<WorkerModule>) =
        Golden.assertMatches("relationships-$name.jsonl", Golden.relationships(analyzeAll(modules)))

    @Test
    fun `imports of declarations classes and packages match the golden output`() =
        golden("imports", listOf(RelationSamples.imports))

    @Test
    fun `unresolved ambiguous and probable references without a classpath match the golden output`() =
        golden("unresolved", listOf(RelationSamples.unresolved))

    @Test
    fun `unresolved references of a module with a classpath match the golden output`() =
        golden("unresolved-classpath", listOf(RelationSamples.withClasspath(RelationSamples.unresolved)))

    @Test
    fun `a file with syntax errors keeps the references of its intact parts in the golden output`() =
        golden("syntax-errors", listOf(RelationSamples.broken))

    @Test
    fun `access to internal declarations of a friend module matches the golden output`() =
        golden("friends", RelationSamples.friends)

    @Test
    fun `access to internal declarations without friendship matches the golden output`() =
        golden("strangers", RelationSamples.strangers)
}

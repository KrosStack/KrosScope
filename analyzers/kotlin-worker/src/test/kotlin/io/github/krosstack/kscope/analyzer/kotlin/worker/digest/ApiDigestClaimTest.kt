package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiDigestClaimTest {
    @Test
    fun `equal digests preserve whole dependent fact lists for every removable mutation`(@TempDir root: Path) {
        val fixture = DigestFixture(root)
        val source = fixture.file("Api.kt", SameApiSamples.source)
        val consumer = fixture.file("Use.kt", CONSUMER)
        val modules = modules(source, consumer)
        val batches = analyzeAll(modules)
        val baseline = batches.flatMap { it.facts }
        Edges.assertHas(
            Edges().of(batches),
            "b@main use/consume(). calls -> a@main api/expression(Int).",
            "b@main use/consume(). calls -> a@main api/selected(Int).",
            "b@main use/consume(). references/callable-reference -> a@main api/body(Int).",
        )
        assertTrue(baseline.any { it is Fact.OfSymbol })
        val digest = fixture.digests(listOf(source)).getValue(source.ref)

        SameApiSamples.mutations().forEach { mutation ->
            Files.writeString(root.resolve("Api.kt"), mutation.after)
            assertEquals(digest, fixture.digests(listOf(source)).getValue(source.ref), mutation.name)
            val actual = analyzeAll(modules).flatMap { it.facts }
            assertEquals(baseline, actual, difference(mutation.name, baseline, actual))
        }
    }

    private fun modules(source: WorkerFile, consumer: WorkerFile): List<WorkerModule> {
        val context = SampleWorkspace.sourceSet("a", "main", "shapes/main", emitFactsFor = false)
            .copy(files = listOf(source))
        val emitting = SampleWorkspace.sourceSet("b", "main", "shapes/main").copy(files = listOf(consumer))
        val a = SampleWorkspace.module("a", Platform.JVM, context)
        val b = SampleWorkspace.module("b", Platform.JVM, emitting).copy(dependencies = listOf("a"))
        return listOf(a, b)
    }

    private fun difference(name: String, expected: List<Fact>, actual: List<Fact>): String {
        val index = (0 until maxOf(expected.size, actual.size)).firstOrNull {
            expected.getOrNull(it) != actual.getOrNull(it)
        }
        return "$name: differing fact $index: ${index?.let(expected::getOrNull)} != ${index?.let(actual::getOrNull)}"
    }

    private companion object {
        val CONSUMER = """
            package use
            import api.*
            fun consume(): Int {
                val box = Box()
                box.value = body(1)
                val reference = ::body
                val result = selected(expression(reference(box.block(property))))
                return result + constant + implementation.run()
            }
        """.trimIndent()
    }
}

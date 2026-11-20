package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextRunTest.Companion.hash
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextRunTest.Fixture
import io.github.krosstack.kscope.model.KscopeJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticContextCheckpointTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `changed product or component identity rejects outer completed records`() = Fixture(root).use { f ->
        val first = f.runner().run(f.manifest)
        assertTrue(first.all { it.complete })
        val queries = f.fake.queriesEmbedded
        for (recipe in listOf("product", "worker", "descriptor", "runtime")) {
            assertFailsWith<IllegalArgumentException> {
                SemanticContextRun(
                    f.options,
                    { _, _ -> error("Must reject before opening") },
                    { _, _ -> error("Must reject before reading") },
                    { hash(recipe) },
                ).run(f.manifest)
            }
        }
        assertEquals(queries, f.fake.queriesEmbedded)
    }

    @Test
    fun `changed ML arm or provider cannot resume the other arm`() = Fixture(root).use { f ->
        val record = f.runner().run(f.manifest).first()
        val checkpoint = SemanticContextCheckpoint(f.options.output)
        val original = assertNotNull(record.original)
        val offOnly = original.copy(
            budgets = original.budgets.map { budget ->
                budget.copy(
                    tasks = budget.tasks.map { task ->
                        task.copy(methods = task.methods.filterKeys { it == ContextBenchmarkMethod.FULL_COMPILER })
                    },
                )
            },
        )
        val provider = assertNotNull(record.pipeline).copy(provider = "another-provider")
        val wrongKind = record.copy(original = original.copy(kind = BenchmarkSet.HELD_OUT))
        for (changed in listOf(record.copy(pipeline = provider), record.copy(original = offOnly), wrongKind)) {
            checkpoint.save(changed)
            assertNull(checkpoint.load(record.task, record.identity))
        }
    }

    @Test
    fun `unchanged task and generation resume without embedding again`() = Fixture(root).use { f ->
        val first = f.runner().run(f.manifest)
        assertTrue(first.all { it.complete })
        val queries = f.fake.queriesEmbedded
        val texts = f.fake.textsEmbedded
        val pipeline = assertNotNull(first.first().pipeline)
        val retired = "embeddings-${pipeline.provider}-${assertNotNull(pipeline.generation).value}.bin"
        Files.walk(f.options.output.resolve("indexes")).use { files ->
            files.filter { it.fileName.toString() == retired }.forEach(Files::delete)
        }
        assertFalse(
            Files.walk(f.options.output.resolve("indexes")).use { files ->
                files.anyMatch { it.fileName.toString() == retired }
            },
        )
        val resumed = SemanticContextRun(
            f.options,
            { _, _ -> error("Completed parent must not open") },
            { _, _ -> error("Retained provenance must suffice") },
            { hash("recipe") },
        ).run(f.manifest)
        assertEquals(first, resumed)
        assertEquals(queries, f.fake.queriesEmbedded)
        assertEquals(texts, f.fake.textsEmbedded)
        f.assertIsolated()
    }

    @Test
    fun `changed parent or query set invalidates completion`() = Fixture(root).use { f ->
        val first = f.runner().run(f.manifest)
        assertTrue(first.all { it.complete })
        val changed = f.manifest.copy(tasks = f.manifest.tasks.map { it.copy(parent = it.commit) })
        assertFailsWith<IllegalArgumentException> { f.runner().run(changed) }
        f.queries("Another query")
        assertFailsWith<IllegalArgumentException> { f.runner().run(f.manifest) }
        assertEquals(6, f.fake.queriesEmbedded)
    }

    @Test
    fun `old output and altered provenance are refused without patching records`() = Fixture(root).use { f ->
        val first = f.runner().run(f.manifest)
        val checkpoint = SemanticContextCheckpoint(f.options.output)
        val record = first.first()
        assertNotNull(checkpoint.load(record.task, record.identity))
        val path = Files.list(f.options.output.resolve("tasks")).use { it.sorted().findFirst().orElseThrow() }
        val bytes = Files.readString(path)
        Files.writeString(path, bytes.replace("\"inventory\":\"", "\"inventory\":\"0"))
        assertFailsWith<IllegalArgumentException> { f.runner().run(f.manifest) }
        assertEquals(bytes.replace("\"inventory\":\"", "\"inventory\":\"0"), Files.readString(path))
        val old = Files.createDirectories(root.resolve("old/tasks"))
        Files.writeString(old.resolve("old.json"), "{}")
        assertFailsWith<IllegalArgumentException> {
            SemanticContextCheckpoint(old.parent).bind(KscopeJson.parseToJsonElement("{}"))
        }
        assertNull(checkpoint.load(record.task.copy(id = "absent"), record.identity))
    }
}

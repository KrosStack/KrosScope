package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.semantic.store.GenerationFile
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class CurrentSemanticGenerationReuseTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `admission decodes once and checks the manifest without reopening`() = fixture { f ->
        ready(f)
        assertEquals(1, f.generationOpens)
        f.current.close()
        f.assertNoLeases()
    }

    @Test
    fun `unchanged snapshots skip decoding and each identity change admits again`() = fixture { f ->
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        val changes = listOf<() -> Unit>(
            { f.revision++ },
            { f.component = "component-b" },
            {
                f.cache.write(
                    f.provider.identity,
                    f.inventory.map { record ->
                        record.copy(chunks = record.chunks.map { it.copy(nodes = emptyList()) })
                    },
                )
            },
            { f.store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1))) },
        )
        var previous = ready(f)
        for (change in changes) {
            val before = f.generationOpens
            assertSame(previous, ready(f))
            assertEquals(before, f.generationOpens)
            change()
            val changed = ready(f)
            assertNotSame(previous, changed)
            assertEquals(before + 1, f.generationOpens)
            previous = changed
        }
        f.current.close()
        f.assertNoLeases()
    }

    @Test
    fun `admission and generation only changes share one inventory validation`() = fixture { f ->
        ready(f)
        assertEquals(f.validationQueries, f.symbolQueries)
        f.component = "component-b"
        ready(f)
        assertEquals(f.validationQueries, f.symbolQueries)
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        f.store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1)))
        ready(f)
        assertEquals(f.validationQueries, f.symbolQueries)
    }

    @Test
    fun `unchanged second open does not query symbols`() = fixture { f ->
        val previous = ready(f)
        f.symbolQueries = 0
        assertSame(previous, ready(f))
        assertEquals(0, f.symbolQueries)
    }

    @Test
    fun `cache byte changes validate once even when inventory values are unchanged`() = fixture { f ->
        val previous = ready(f)
        f.symbolQueries = 0
        Files.writeString(f.directory.resolve("embeddings-chunks.json"), "\n", StandardOpenOption.APPEND)
        val changed = ready(f)
        assertEquals(f.validationQueries, f.symbolQueries)
        assertNotSame(previous, changed)
        f.symbolQueries = 0
        assertSame(changed, ready(f))
        assertEquals(0, f.symbolQueries)
    }

    @Test
    fun `graph revision changes validate once before admitting again`() = fixture { f ->
        val previous = ready(f)
        f.symbolQueries = 0
        f.revision++
        val changed = ready(f)
        assertEquals(f.validationQueries, f.symbolQueries)
        assertNotSame(previous, changed)
        f.symbolQueries = 0
        assertSame(changed, ready(f))
        assertEquals(0, f.symbolQueries)
    }

    private fun ready(f: SemanticQueryFixture) =
        assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider)).snapshot

    private fun fixture(test: (SemanticQueryFixture) -> Unit) = SemanticQueryFixture(root).use(test)
}

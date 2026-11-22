package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.semantic.SemanticFixture
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenerationPublicationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `rebuild invalidates preparation even when the manifest stays absent`() {
        val store = GenerationStore(directory)
        store.prepare(generation("pending")).use { prepared ->
            GenerationStore(directory).rebuild()
            assertEquals(null, store.current("fake"))
            assertEquals(
                PublishResult.Deferred("Generation changed during preparation"),
                store.publish(prepared, null),
            )
            assertEquals(null, store.current("fake"))
        }
        assertTrue(Files.list(directory).use { it.noneMatch { path -> path.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `rebuild cannot delete staging while preparation is writing`() {
        val written = CountDownLatch(1)
        val rebuilding = CountDownLatch(1)
        val rebuilt = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = GenerationStore(directory, { 0L }) { path, bytes ->
            Files.write(path, bytes)
            written.countDown()
            check(rebuilding.await(10, TimeUnit.SECONDS))
            try {
                assertFalse(rebuilt.await(1, TimeUnit.SECONDS))
                assertTrue(Files.exists(path))
            } finally {
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val preparation = executor.submit<PreparedGeneration> { store.prepare(generation("pending")) }
            check(written.await(10, TimeUnit.SECONDS))
            val rebuild = executor.submit {
                rebuilding.countDown()
                GenerationStore(directory).rebuild()
                rebuilt.countDown()
            }
            release.countDown()
            preparation.get(10, TimeUnit.SECONDS).use { prepared ->
                rebuild.get(10, TimeUnit.SECONDS)
                assertEquals(
                    PublishResult.Deferred("Generation changed during preparation"),
                    store.publish(prepared, null),
                )
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        assertTrue(Files.list(directory).use { it.noneMatch { path -> path.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `changed manifest rejects a prepared generation`() {
        val store = GenerationStore(directory)
        assertEquals(null, store.current("fake"))
        store.publish(generation("base"))
        val expected = store.current("fake")
        store.prepare(generation("pending")).use { prepared ->
            store.publish(generation("newer"))
            val name = store.current("fake")
            val bytes = Files.readAllBytes(directory.resolve("embeddings.json"))
            assertIs<PublishResult.Deferred>(store.publish(prepared, null))
            assertIs<PublishResult.Deferred>(store.publish(prepared, expected))
            assertEquals(name, store.current("fake"))
            assertContentEquals(bytes, Files.readAllBytes(directory.resolve("embeddings.json")))
        }
    }

    @Test
    fun `deferred publication keeps manifest bytes and prepared vectors`() {
        val store = GenerationStore(directory)
        val readers = (1..3).map {
            store.publish(generation("v$it"))
            requireNotNull(store.open("fake"))
        }
        try {
            val expected = store.current("fake")
            val before = Files.readAllBytes(directory.resolve("embeddings.json"))
            store.prepare(generation("next")).use { prepared ->
                assertIs<PublishResult.Deferred>(store.publish(prepared, expected))
                assertContentEquals(before, Files.readAllBytes(directory.resolve("embeddings.json")))
                readers.first().close()
                val published = assertIs<PublishResult.Published>(store.publish(prepared, expected))
                val bytes = Files.readAllBytes(directory.resolve(published.generation))
                assertContentEquals(generation("next").bytes(), bytes)
            }
        } finally {
            readers.forEach { it.close() }
        }
    }

    @Test
    fun `failed staging cleans up and leaves the manifest unchanged`() {
        GenerationStore(directory).publish(generation("old"))
        val before = Files.readAllBytes(directory.resolve("embeddings.json"))
        val store = GenerationStore(directory, { 0L }) { path, bytes ->
            Files.write(path, bytes.copyOf(1))
            throw IOException("Write failed")
        }
        assertFailsWith<IOException> { store.prepare(generation("next")) }
        assertContentEquals(before, Files.readAllBytes(directory.resolve("embeddings.json")))
        assertTrue(Files.list(directory).use { it.noneMatch { path -> path.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `closing an unpublished preparation removes its file`() {
        val store = GenerationStore(directory)
        val prepared = store.prepare(generation("pending"))
        assertTrue(Files.list(directory).use { it.anyMatch { path -> path.fileName.toString().endsWith(".tmp") } })
        prepared.close()
        prepared.close()
        assertTrue(Files.list(directory).use { it.noneMatch { path -> path.fileName.toString().endsWith(".tmp") } })
        assertFailsWith<IllegalStateException> { store.publish(prepared, null) }
    }

    private fun generation(path: String): GenerationFile = SemanticFixture().apply { file(path) }.generation()
}

package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.semantic.SemanticFixture
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerationStoreRecoveryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `rebuild removes generations manifest and leases even with open readers`() {
        val store = GenerationStore(directory)
        val published = assertIs<PublishResult.Published>(store.publish(generation("old")))
        store.open("fake").use { reader ->
            assertNotNull(reader)
            store.rebuild()
            reader.renew()
            assertFalse(Files.exists(directory.resolve(published.generation)))
            assertFalse(Files.exists(directory.resolve("embeddings-leases")))
            assertNull(store.open("fake"))
            assertEquals(1, reader.generation.chunks.size)
        }
    }

    @Test
    fun `corrupt generations and malformed manifests are absent`() {
        val store = GenerationStore(directory)
        val published = assertIs<PublishResult.Published>(store.publish(generation("old")))
        Files.write(directory.resolve(published.generation), byteArrayOf(1))
        assertNull(store.open("fake"))
        Files.writeString(directory.resolve("embeddings.json"), "{broken}")
        assertNull(store.open("fake"))
        store.publish(generation("old"))
        store.open("fake").use { assertNotNull(it) }
    }

    @Test
    fun `rebuild removes publishing temporary files`() {
        val temporary = Files.createTempFile(directory, "publishing-", ".tmp")
        GenerationStore(directory).rebuild()
        assertFalse(Files.exists(temporary))
    }

    @Test
    fun `rebuild waits for publication without deleting its temporary file`() {
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        val rebuilding = CountDownLatch(1)
        val store = GenerationStore(directory, { 0L }) { path, bytes ->
            Files.write(path, bytes)
            written.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publication = executor.submit<PublishResult> { store.publish(generation("target")) }
            check(written.await(10, TimeUnit.SECONDS))
            val rebuild = executor.submit {
                rebuilding.countDown()
                GenerationStore(directory).rebuild()
            }
            check(rebuilding.await(10, TimeUnit.SECONDS))
            release.countDown()
            assertIs<PublishResult.Published>(publication.get(10, TimeUnit.SECONDS))
            rebuild.get(10, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `corrupt target with live lease defers publication`() {
        val store = GenerationStore(directory)
        val generation = generation("target")
        val published = assertIs<PublishResult.Published>(store.publish(generation))
        store.open("fake").use {
            Files.write(directory.resolve(published.generation), byteArrayOf(1))
            assertEquals(PublishResult.Deferred("Corrupt generation still leased"), store.publish(generation))
        }
    }

    @Test
    fun `corrupt target without lease is replaced`() {
        val store = GenerationStore(directory)
        val generation = generation("target")
        val published = assertIs<PublishResult.Published>(store.publish(generation))
        Files.write(directory.resolve(published.generation), byteArrayOf(1))
        store.publish(generation)
        kotlin.test.assertContentEquals(generation.bytes(), Files.readAllBytes(directory.resolve(published.generation)))
    }

    @Test
    fun `valid generation copied under another name is absent`() {
        val store = GenerationStore(directory)
        val published = assertIs<PublishResult.Published>(store.publish(generation("target")))
        Files.write(directory.resolve(published.generation), generation("other").bytes())
        assertNull(store.open("fake"))
    }

    @Test
    fun `valid generation copied under target name is replaced by publication`() {
        val store = GenerationStore(directory)
        val generation = generation("target")
        val published = assertIs<PublishResult.Published>(store.publish(generation))
        Files.write(directory.resolve(published.generation), generation("other").bytes())
        store.publish(generation)
        kotlin.test.assertContentEquals(generation.bytes(), Files.readAllBytes(directory.resolve(published.generation)))
    }

    private fun generation(text: String): GenerationFile = SemanticFixture().apply { file(text = text) }
        .generation().let { file ->
            val entry = file.chunks.single()
            val key = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.toByteArray()),
            )
            GenerationFile(file.header, listOf(entry.copy(chunk = entry.chunk.copy(key = key))))
        }
}

package io.github.krosstack.kscope.semantic.store

import com.sun.nio.file.ExtendedOpenOption
import io.github.krosstack.kscope.semantic.SemanticFixture
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerationStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `leased generations survive publication and last close releases shared process lease`() {
        var time = 10_000L
        val store = GenerationStore(directory) { time }
        val first = assertIs<PublishResult.Published>(store.publish(generation("first")))
        val reader = assertNotNull(store.open("fake"))
        val secondReader = assertNotNull(store.open("fake"))
        val second = assertIs<PublishResult.Published>(store.publish(generation("second")))
        assertTrue(Files.exists(directory.resolve(first.generation)))
        reader.close()
        time += 60_000
        secondReader.renew()
        store.publish(generation("third"))
        assertTrue(Files.exists(directory.resolve(first.generation)))
        assertFalse(Files.exists(directory.resolve(second.generation)))
        secondReader.close()
        store.publish(generation("fourth"))
        assertFalse(Files.exists(directory.resolve(first.generation)))
    }

    @Test
    fun `stale lease is ignored while newest remains readable`() {
        val store = GenerationStore(directory) { 1_000_000L }
        val first = assertIs<PublishResult.Published>(store.publish(generation("old")))
        val leases = Files.createDirectories(directory.resolve("embeddings-leases"))
        val stale = Files.write(leases.resolve("${first.generation}-999"), byteArrayOf())
        Files.setLastModifiedTime(stale, FileTime.fromMillis(0))
        store.publish(generation("new"))
        assertFalse(Files.exists(directory.resolve(first.generation)))
        store.open("fake").use { assertNotNull(it) }
    }

    @Test
    fun `publication defers when more than three generations would survive`() {
        val store = GenerationStore(directory)
        val readers = (1..3).map {
            assertIs<PublishResult.Published>(store.publish(generation("v$it")))
            assertNotNull(store.open("fake"))
        }
        try {
            val before = Files.readString(directory.resolve("embeddings.json"))
            assertIs<PublishResult.Deferred>(store.publish(generation("fourth")))
            assertEquals(before, Files.readString(directory.resolve("embeddings.json")))
            readers.first().close()
            assertIs<PublishResult.Published>(store.publish(generation("fourth")))
        } finally {
            readers.forEach { it.close() }
        }
    }

    @Test
    fun `publication and reclamation keep providers independent`() {
        val store = GenerationStore(directory)
        val first = generation("first")
        val other = GenerationFile(first.header.copy(provider = "another"), first.chunks)
        store.publish(first)
        val published = assertIs<PublishResult.Published>(store.publish(other))
        store.publish(generation("second"))
        assertTrue(Files.exists(directory.resolve(published.generation)))
        store.open("another").use { assertEquals("another", assertNotNull(it).generation.header.provider) }
    }

    @Test
    fun `open old generation does not fail publication and is reclaimed later on Windows`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val store = GenerationStore(directory)
        val first = assertIs<PublishResult.Published>(store.publish(generation("old")))
        val old = directory.resolve(first.generation)
        FileChannel.open(old, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE).use {
            assertIs<PublishResult.Published>(store.publish(generation("new")))
            assertTrue(Files.exists(old))
        }
        store.publish(generation("later"))
        assertFalse(Files.exists(old))
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

package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.semantic.SemanticFixture
import org.junit.jupiter.api.io.TempDir
import java.io.FilterInputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerationQueryOpeningTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `publication completes while a query is decoding its leased generation`() {
        val store = GenerationStore(directory) { 0L }
        val published = assertIs<PublishResult.Published>(store.publish(generation()))
        val decoding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val access = GenerationAccess(directory.toAbsolutePath().normalize(), { 0L }) { path ->
            object : FilterInputStream(Files.newInputStream(path)) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    decoding.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return super.read(bytes, offset, length)
                }
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        val opening = executor.submit<GenerationOpening> { access.tryOpen("fake") }
        try {
            check(decoding.await(10, TimeUnit.SECONDS))
            val publication = executor.submit<PublishResult> {
                store.publish(SemanticFixture().apply { file("new.kt") }.generation())
            }
            assertIs<PublishResult.Published>(publication.get(5, TimeUnit.SECONDS))
            assertTrue(Files.exists(directory.resolve(published.generation)))
            release.countDown()
            val decoded = assertIs<GenerationOpening.Opened>(opening.get(10, TimeUnit.SECONDS))
            assertEquals(published.generation, decoded.name)
        } finally {
            release.countDown()
            try {
                (runCatching { opening.get(10, TimeUnit.SECONDS) }.getOrNull() as? GenerationOpening.Opened)
                    ?.lease?.close()
            } finally {
                executor.shutdownNow()
                check(executor.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
        assertNoLeases()
        store.publish(SemanticFixture().apply { file("last.kt") }.generation())
        assertTrue(!Files.exists(directory.resolve(published.generation)))
    }

    @Test
    fun `current name read needs neither a generation file nor a lease`() {
        val store = GenerationStore(directory)
        val published = assertIs<PublishResult.Published>(store.publish(generation()))
        Files.delete(directory.resolve(published.generation))
        assertEquals(GenerationCurrent.Current(published.generation), store.tryCurrent("fake"))
        assertIs<GenerationCurrent.Absent>(store.tryCurrent("other"))
        assertNoLeases()
    }

    @Test
    fun `current name read distinguishes absence from invalid and unreadable manifests`() {
        val store = GenerationStore(directory)
        assertIs<GenerationCurrent.Absent>(store.tryCurrent("fake"))
        for (manifest in listOf("{", "{\"fake\":\"../escape.bin\"}")) {
            Files.writeString(directory.resolve("embeddings.json"), manifest)
            assertIs<GenerationCurrent.Unavailable>(store.tryCurrent("fake"))
        }
        Files.delete(directory.resolve("embeddings.json"))
        Files.createDirectory(directory.resolve("embeddings.json"))
        assertIs<GenerationCurrent.Unavailable>(store.tryCurrent("fake"))
        assertNoLeases()
    }

    @Test
    fun `current name read returns busy under local publication and file contention`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = GenerationStore(directory, { 0L }) { path, bytes ->
            Files.write(path, bytes)
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publication = executor.submit<PublishResult> { store.publish(generation()) }
            check(entered.await(10, TimeUnit.SECONDS))
            assertIs<GenerationCurrent.Busy>(
                executor.submit<GenerationCurrent> { GenerationStore(directory).tryCurrent("fake") }
                    .get(5, TimeUnit.SECONDS),
            )
            release.countDown()
            assertIs<PublishResult.Published>(publication.get(10, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        FileChannel.open(directory.resolve("embeddings.lock"), StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { assertIs<GenerationCurrent.Busy>(store.tryCurrent("fake")) }
        }
        assertNoLeases()
    }

    @Test
    fun `busy local admission returns without waiting and creates no lease`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = GenerationStore(directory, { 0L }) { path, bytes ->
            Files.write(path, bytes)
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publication = executor.submit<PublishResult> { store.publish(generation()) }
            check(entered.await(10, TimeUnit.SECONDS))
            assertIs<GenerationOpening.Busy>(
                executor.submit<GenerationOpening> { GenerationStore(directory).tryOpen("fake") }
                    .get(10, TimeUnit.SECONDS),
            )
            assertNoLeases()
            release.countDown()
            assertIs<PublishResult.Published>(publication.get(10, TimeUnit.SECONDS))
            assertNotNull(store.open("fake")).use { assertEquals("fake", it.generation.header.provider) }
        } finally {
            release.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        assertNoLeases()
    }

    @Test
    fun `busy file lock admission creates no lease`() {
        FileChannel.open(directory.resolve("embeddings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel ->
                channel.lock().use {
                    assertIs<GenerationOpening.Busy>(GenerationStore(directory).tryOpen("fake"))
                    assertNoLeases()
                }
            }
    }

    @Test
    fun `corrupt pointed generations and malformed manifests differ from absence`() {
        val store = GenerationStore(directory)
        assertIs<GenerationOpening.Absent>(store.tryOpen("fake"))
        val published = assertIs<PublishResult.Published>(store.publish(generation()))
        assertIs<GenerationOpening.Absent>(store.tryOpen("other"))
        Files.write(directory.resolve(published.generation), byteArrayOf(1))
        assertIs<GenerationOpening.Unavailable>(store.tryOpen("fake"))
        Files.delete(directory.resolve(published.generation))
        assertIs<GenerationOpening.Unavailable>(store.tryOpen("fake"))
        for (manifest in listOf("{", "{\"fake\":\"../escape.bin\"}")) {
            Files.writeString(directory.resolve("embeddings.json"), manifest)
            assertIs<GenerationOpening.Unavailable>(store.tryOpen("fake"))
        }
        val original = generation()
        val foreign = GenerationFile(original.header.copy(provider = "foreign"), original.chunks).bytes()
        val digest = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(foreign),
        )
        val name = "embeddings-fake-$digest.bin"
        Files.write(directory.resolve(name), foreign)
        Files.writeString(directory.resolve("embeddings.json"), "{\"fake\":\"$name\"}")
        assertIs<GenerationOpening.Unavailable>(store.tryOpen("fake"))
        assertNoLeases()
    }

    @Test
    fun `selected generation stays leased across publication until its caller closes`() {
        val store = GenerationStore(directory)
        val published = assertIs<PublishResult.Published>(store.publish(generation()))
        val opened = assertIs<GenerationOpening.Opened>(store.tryOpen("fake"))
        try {
            assertEquals(published.generation, opened.name)
            store.publish(SemanticFixture().apply { file("new.kt") }.generation())
            assertTrue(Files.exists(directory.resolve(published.generation)))
            assertEquals("sample.kt", opened.lease.generation.chunks.single().chunk.artifact.path.value)
        } finally {
            opened.lease.close()
        }
        assertNoLeases()
        store.publish(SemanticFixture().apply { file("last.kt") }.generation())
        assertTrue(!Files.exists(directory.resolve(published.generation)))
    }

    private fun generation() = SemanticFixture().apply { file() }.generation()

    private fun assertNoLeases() {
        val leases = directory.resolve("embeddings-leases")
        assertTrue(!Files.exists(leases) || Files.list(leases).use { it.findAny().isEmpty })
    }
}

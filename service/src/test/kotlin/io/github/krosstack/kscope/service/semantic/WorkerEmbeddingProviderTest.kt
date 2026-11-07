package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorkerEmbeddingProviderTest {
    @Test
    fun `documents queries and caps travel through a child JVM`(@TempDir directory: Path) {
        provider(directory).use { provider ->
            assertEquals(17, provider.embed(List(17) { "abc" }).size)
            assertEquals(listOf(0f, 3f), provider.embed(listOf("abc")).single().toList())
            assertEquals(listOf(1f, 3f), provider.embedQuery("abc").toList())
            assertEquals("abc", provider.cap("abcdef", 3))
        }
        assertStopped(directory)
    }

    @Test
    fun `silent query reaches its deadline and stops the process`(@TempDir directory: Path) {
        provider(directory, "silent").use { provider ->
            assertFailsWith<IOException> { provider.embedQuery("query") }
        }
        assertStopped(directory)
    }

    @Test
    fun `silent document batch reaches its deadline and stops the process`(@TempDir directory: Path) {
        provider(directory, "silent", batchTimeout = 1.seconds).use { provider ->
            assertFailsWith<IOException> { provider.embed(listOf("document")) }
        }
        assertStopped(directory)
    }

    @Test
    fun `unexpected response fails and closes the process`(@TempDir directory: Path) {
        provider(directory, "bad").use { provider ->
            assertFailsWith<IOException> { provider.embed(listOf("document")) }
        }
        assertStopped(directory)
    }

    @Test
    fun `close ends the child and prevents another exchange`(@TempDir directory: Path) {
        val provider = provider(directory)
        provider.close()
        assertFailsWith<IllegalStateException> { provider.embedQuery("query") }
        provider.close()
        assertStopped(directory)
    }

    @Test
    fun `deadline stops descendants too`(@TempDir directory: Path) {
        provider(directory, "silent-tree").use { provider ->
            assertFailsWith<IOException> { provider.embedQuery("query") }
        }
        val child = ProcessHandle.of(directory.resolve("pid-child").readText().toLong())
        child.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
        assertTrue(child.isEmpty || !child.get().isAlive)
        assertStopped(directory)
    }

    @Test
    fun `mismatched handshake stops the process`(@TempDir directory: Path) {
        assertFailsWith<IOException> { provider(directory, "mismatch") }
        assertStopped(directory)
    }

    @Test
    fun `installed component loads native inference in its child JVM`() {
        val directory = System.getenv("KSCOPE_SEMANTIC_COMPONENT")
        assumeTrue(directory != null, "Installed semantic component is absent")
        WorkerEmbeddingProvider(Path.of(requireNotNull(directory))).use { provider ->
            val vector = provider.embedQuery("find factorial implementation")
            assertEquals(768, vector.size)
            assertEquals(1.0, sqrt(vector.sumOf { it.toDouble() * it }), 0.00001)
            assertTrue(provider.cap("hello world", 3).length < 11)
        }
    }

    private fun provider(
        directory: Path,
        mode: String = "normal",
        batchTimeout: kotlin.time.Duration = 10.seconds,
    ): WorkerEmbeddingProvider = WorkerEmbeddingProvider(
        ModelIdentity("fake", "test", 2, "int8-dynamic", 1),
        listOf(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("kscope.semantic.test.classpath"),
            FakeEmbeddingMain::class.java.name,
            directory.resolve("pid").toString(),
            mode,
        ),
        batchTimeout,
        100.milliseconds,
    )

    private fun assertStopped(directory: Path) {
        val handle = ProcessHandle.of(directory.resolve("pid").readText().toLong())
        if (handle.isPresent) {
            handle.get().onExit().get(5, TimeUnit.SECONDS)
            assertFalse(handle.get().isAlive)
        } else {
            assertTrue(handle.isEmpty)
        }
    }
}

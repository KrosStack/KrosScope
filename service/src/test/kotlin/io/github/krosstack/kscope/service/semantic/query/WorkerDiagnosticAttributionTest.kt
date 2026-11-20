package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import io.github.krosstack.kscope.service.semantic.WorkerProcess
import io.github.krosstack.kscope.service.semantic.process.WorkerDiagnostics
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WorkerDiagnosticAttributionTest {
    private val read = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val delay = AtomicBoolean()
    private val queries = CopyOnWriteArrayList<String>()
    private val indexing = CopyOnWriteArrayList<String>()
    private val handshake = CountDownLatch(1)

    @Test
    fun `chunked diagnostics preserve complete lines and flush a closed tail`() {
        val routing = WorkerDiagnostics.Routing(indexing::add, read = { stream, buffer -> stream.read(buffer, 0, 1) })
        val process = DiagnosticProcess("first\r\n\nlast\r")
        val diagnostics = WorkerDiagnostics(process, routing, queries::add, { true }, false)
        diagnostics.await({ TimeUnit.SECONDS.toNanos(3) }, true)
        assertEquals(listOf("first", "", "last"), queries)
        assertEquals(queries, indexing)
    }

    @Test
    fun `an oversized diagnostic line is omitted through its newline`() {
        val full = "x".repeat(8192)
        val diagnostics = WorkerDiagnostics(
            DiagnosticProcess(full + "end\nnext\n"),
            WorkerDiagnostics.Routing(indexing::add),
            queries::add,
            { true },
            false,
        )
        diagnostics.await({ TimeUnit.SECONDS.toNanos(3) }, true)
        assertEquals(listOf("Semantic diagnostic line over 8192 characters omitted", "next"), queries)
        assertEquals(queries, indexing)
    }

    @Test
    fun `a path crossing the diagnostic character limit leaves no retained fragment`(@TempDir root: Path) {
        val components = root.resolve("sensitive-components").resolve("x".repeat(8192)).toString()
        val source = root.resolve("Diagnostic.java")
        Files.writeString(
            source,
            """
            class Diagnostic {
                public static void main(String[] args) {
                    System.err.println("Caused by: " + args[0]);
                    System.err.println("next");
                }
            }
            """.trimIndent(),
        )
        val command = listOf(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            source.toString(),
            components,
        )
        WorkerProcess(command, { it.replace(components, "<components>") }, WorkerDiagnostics.Routing(indexing::add))
            .use { worker ->
                val failure = assertFailsWith<IOException> {
                    worker.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
                }
                val diagnostic = failure.message.orEmpty().substringAfter("\n")
                assertFalse(diagnostic.contains(root.fileName.toString()))
                assertEquals("Semantic diagnostic line over 8192 characters omitted\nnext", diagnostic)
            }
    }

    @Test
    fun `end of stream and read failure flush the incomplete diagnostic tail`() {
        for (failure in listOf(false, true)) {
            queries.clear()
            indexing.clear()
            val first = AtomicBoolean(true)
            val routing = WorkerDiagnostics.Routing(indexing::add, read = { stream, buffer ->
                when {
                    first.getAndSet(false) -> stream.read(buffer, 0, 4)
                    failure -> throw IOException("closed")
                    else -> -1
                }
            })
            val process = DiagnosticProcess("tail unread")
            val diagnostics = WorkerDiagnostics(process, routing, queries::add, { true }, false)
            diagnostics.await({ TimeUnit.SECONDS.toNanos(3) }, true)
            assertEquals(listOf("tail"), queries)
            assertEquals(if (failure) queries + "Semantic diagnostics stream closed" else queries, indexing)
        }
    }

    @Test
    fun `a blocked indexing sink does not stop stderr retention or the reply`(@TempDir root: Path) {
        val pool = Executors.newSingleThreadExecutor()
        val process = ProcessBuilder(command(root, "burst-stderr")).start()
        val closed = AtomicBoolean()
        val retained = CountDownLatch(64)
        val routing = WorkerDiagnostics.Routing({
            read.countDown()
            release.await()
        })
        val diagnostics = WorkerDiagnostics(process, routing, {
            queries.add(it)
            retained.countDown()
        }, closed::get, false)
        try {
            val reply = pool.submit<EmbeddingResponse> {
                process.outputStream.bufferedWriter().apply {
                    write(EmbeddingProtocol.encode(EmbeddingRequest.Embed("indexing", listOf("text"), false)))
                    newLine()
                    flush()
                }
                EmbeddingProtocol.decodeResponse(process.inputStream.bufferedReader().readLine())
            }
            assertTrue(read.await(3, TimeUnit.SECONDS))
            assertIs<EmbeddingResponse.Vectors>(reply.get(3, TimeUnit.SECONDS))
            assertTrue(retained.await(3, TimeUnit.SECONDS))
            assertEquals(1L, release.count)
            assertEquals((0 until 64).map { "Diagnostic $it: " + "x".repeat(1024) }, queries)
        } finally {
            release.countDown()
            closed.set(true)
            process.destroyForcibly()
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            diagnostics.await({ TimeUnit.SECONDS.toNanos(3) }, true)
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertStopped(root)
    }

    @Test
    fun `an incomplete diagnostic line does not block the next exchange`(@TempDir root: Path) {
        val pool = Executors.newSingleThreadExecutor()
        val partial = CountDownLatch(1)
        val routing = WorkerDiagnostics.Routing(indexing::add, read = { stream, buffer ->
            stream.read(buffer, 0, buffer.size).also { count ->
                if (count > 0 && buffer.concatToString(0, count).contains("partial")) partial.countDown()
            }
        })
        try {
            WorkerProcess(command(root, "partial-stderr"), routing = routing).use { worker ->
                sendQuery(worker)
                assertTrue(partial.await(3, TimeUnit.SECONDS))
                val next = pool.submit<EmbeddingResponse> {
                    worker.exchange(EmbeddingRequest.Embed("indexing", listOf("text"), false), 10.seconds)
                }
                assertTrue(next.get(3, TimeUnit.SECONDS) is EmbeddingResponse.Vectors)
                assertTrue(queries.none { it.contains("partial requestId=query") })
                assertTrue(indexing.none { it.contains("partial requestId=query") })
            }
            assertTrue(queries.contains("partial requestId=query"))
            assertEquals(1, queries.count { it == "partial requestId=query" })
            assertTrue(indexing.none { it.contains("partial requestId=query") })
            assertStopped(root)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `a delayed query diagnostic is forwarded before indexing switches its sink`(@TempDir root: Path) {
        val pool = Executors.newSingleThreadExecutor()
        val routing = WorkerDiagnostics.Routing(indexing::add, read = { stream, buffer ->
            if (delay.compareAndSet(true, false)) {
                read.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            stream.read(buffer, 0, buffer.size)
        })
        try {
            WorkerProcess(command(root), routing = routing).use { worker ->
                sendQuery(worker)
                assertTrue(read.await(5, TimeUnit.SECONDS))
                val next = pool.submit {
                    worker.exchange(EmbeddingRequest.Embed("indexing", listOf("text"), false), 10.seconds)
                }
                assertFailsWith<TimeoutException> { next.get(100, TimeUnit.MILLISECONDS) }
                release.countDown()
                next.get(5, TimeUnit.SECONDS)
                assertTrue(queries.any { it.contains("requestId=query") })
                assertTrue(indexing.none { it.contains("requestId=query") })
            }
            assertStopped(root)
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun sendQuery(worker: WorkerProcess) {
        val deadline = QueryDeadline()
        worker.exchange(
            EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION),
            deadline.remaining(),
            deadline,
            { line ->
                queries.add(line)
                handshake.countDown()
            },
        )
        assertTrue(handshake.await(5, TimeUnit.SECONDS))
        delay.set(true)
        worker.exchange(
            EmbeddingRequest.Embed("query", listOf("text"), true),
            deadline.remaining(),
            deadline,
            queries::add,
        )
    }

    private fun assertStopped(root: Path) {
        val handle = ProcessHandle.of(Files.readString(root.resolve("pid")).toLong())
        handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
        assertFalse(handle.isPresent && handle.get().isAlive)
    }

    private fun command(root: Path, mode: String = "request-stderr") = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("kscope.semantic.test.classpath"),
        FakeEmbeddingMain::class.java.name,
        root.resolve("pid").toString(),
        mode,
    )

    private class DiagnosticProcess(private val text: String) : Process() {
        override fun getErrorStream(): InputStream = text.byteInputStream()

        override fun getInputStream(): InputStream = InputStream.nullInputStream()

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() = Unit
    }
}

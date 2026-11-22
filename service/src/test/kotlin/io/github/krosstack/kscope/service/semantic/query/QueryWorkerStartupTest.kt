package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.InstalledComponent
import io.github.krosstack.kscope.service.semantic.ComponentLayout
import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.OptionalProcessBudget
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.WorkerDeadlines
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.io.TempDir
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class QueryWorkerStartupTest {
    @Test
    fun `owned timeout returns before a blocked query sink is released`(@TempDir root: Path) {
        listOf(false, true).forEach { lazy ->
            blockedQueryRefusal(Files.createDirectory(root.resolve(lazy.toString())), lazy)
        }
    }

    @Test
    fun `owned worker failure returns before a blocked query sink is released`(@TempDir root: Path) {
        listOf(false, true).forEach { lazy ->
            blockedQueryRefusal(
                Files.createDirectory(root.resolve(lazy.toString())),
                lazy,
                mode = "inference-failed",
                expected = WorkerEmbeddingProvider.Refusal.WORKER_FAILED,
            )
        }
    }

    private fun blockedQueryRefusal(
        root: Path,
        lazy: Boolean,
        mode: String = "silent-cause",
        expected: WorkerEmbeddingProvider.Refusal = WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT,
    ) {
        val sink = BlockingDiagnostics()
        val provider = WorkerEmbeddingProvider(
            ModelIdentity("fake", "test", 2, "int8-dynamic", 1),
            command(root).dropLast(1) + mode,
            diagnostics = sink,
        )
        val worker = LazyWorkerProvider(provider.identity, provider.id) { _, _ ->
            LazyWorkerProvider.Started(provider, { null }, provider::embedQuery, provider::close)
        }
        try {
            val deadline = cleanupDeadline(sink, lazy, mode)
            val started = TimeSource.Monotonic.markNow()
            val result = if (lazy) {
                worker.embedQuery("text", deadline, sink)
            } else {
                provider.embedQuery("text", deadline, ownsProcess = true, diagnostics = sink)
            }
            val elapsed = started.elapsedNow()
            assertEquals(
                expected,
                assertIs<WorkerEmbeddingProvider.QueryResult.Refused>(result).reason,
            )
            assertTrue(elapsed < 600.milliseconds, "Refusal took $elapsed")
            assertTrue(sink.isBlocked)
            assertTrue(sink.received.await(5, TimeUnit.SECONDS))
            assertTrue(sink.lines.any { it.contains("Missing runtime dependency") })
            val handle = ProcessHandle.of(Files.readString(root.resolve("pid")).toLong())
            handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
            assertTrue(handle.isEmpty || !handle.get().isAlive)
        } finally {
            sink.close()
            worker.close()
            provider.close()
        }
    }

    private fun cleanupDeadline(sink: BlockingDiagnostics, lazy: Boolean, mode: String): QueryDeadline {
        val clock = TestTimeSource()
        val source = if (mode == "silent-cause") {
            clock
        } else {
            ReplyCleanupClock(clock, postReplyRead = if (lazy) 10 else 6) {
                assertTrue(sink.received.await(10, TimeUnit.SECONDS))
            }
        }
        val deadline = QueryDeadline(source)
        if (mode == "silent-cause") {
            assertTrue(sink.received.await(10, TimeUnit.SECONDS))
            clock += 9900.milliseconds
        }
        return deadline
    }

    private class ReplyCleanupClock(
        private val clock: TestTimeSource,
        private val postReplyRead: Int,
        private val beforeCleanup: () -> Unit,
    ) : TimeSource {
        private val reads = AtomicInteger()

        override fun markNow(): TimeMark = object : TimeMark {
            private val started = clock.markNow()

            override fun elapsedNow(): Duration {
                if (reads.incrementAndGet() == postReplyRead) {
                    beforeCleanup()
                    clock += 9900.milliseconds
                }
                return started.elapsedNow()
            }
        }
    }

    private class BlockingDiagnostics :
        (String) -> Unit,
        AutoCloseable {
        val received = CountDownLatch(1)
        val lines = CopyOnWriteArrayList<String>()
        private val release = CountDownLatch(1)
        private val drained = CountDownLatch(1)

        val isBlocked: Boolean get() = received.count == 0L && release.count == 1L

        override fun invoke(line: String) {
            lines.add(line)
            received.countDown()
            release.await()
            drained.countDown()
        }

        override fun close() {
            release.countDown()
            assertTrue(drained.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `query started worker sends indexing diagnostics to the indexing sink`(@TempDir root: Path) {
        val expected = "Request: ${EmbeddingRequest.Embed("1", listOf("indexing"), false)}"
        val queryDiagnostics = CopyOnWriteArrayList<String>()
        val indexingDiagnostics = CopyOnWriteArrayList<String>()
        val queryStarted = CountDownLatch(1)
        val indexingReceived = CountDownLatch(1)
        val originalErr = System.err
        val indexingSink = object : PrintStream(OutputStream.nullOutputStream()) {
            override fun println(line: String?) {
                indexingDiagnostics.add(line.orEmpty())
                if (line == expected) indexingReceived.countDown()
            }
        }
        indexingSink.use {
            System.setErr(indexingSink)
            try {
                WorkerEmbeddingProvider(
                    ModelIdentity("fake", "test", 2, "int8-dynamic", 1),
                    command(root),
                    WorkerDeadlines(absoluteQuery = QueryDeadline(TestTimeSource())),
                    diagnostics = { line ->
                        queryDiagnostics.add(line)
                        if (line.startsWith("Request: Handshake")) queryStarted.countDown()
                    },
                ).use { provider ->
                    assertTrue(queryStarted.await(5, TimeUnit.SECONDS))
                    assertContentEquals(floatArrayOf(0f, 8f), provider.embed(listOf("indexing")).single())
                    assertTrue(indexingReceived.await(5, TimeUnit.SECONDS), "Indexing stderr did not reach its sink")
                    assertFalse(expected in queryDiagnostics)
                    assertTrue(expected in indexingDiagnostics)
                }
            } finally {
                System.setErr(originalErr)
            }
        }
    }

    @Test
    fun `query startup requires a diagnostic sink before launching`(@TempDir root: Path) {
        install(root)
        val model = root.resolve("semantic-model/1/model")
        val layout = ComponentLayout(root.resolve("semantic/1"), model)
        assertFailsWith<IllegalArgumentException> {
            WorkerEmbeddingProvider(
                layout.identity,
                layout.command(),
                WorkerDeadlines(absoluteQuery = QueryDeadline(TestTimeSource())),
            ).close()
        }
        assertTrue(Files.notExists(model.resolve("pid")))
    }

    @Test
    fun `query startup claims one reservation and captures its diagnostics`(@TempDir root: Path) {
        install(root)
        val clock = TestTimeSource()
        val deadline = QueryDeadline(clock)
        val budget = OptionalProcessBudget(root.resolve("reservations"), allowanceMebibytes = {
            clock += 2.seconds
            3000L
        })
        val components = SemanticComponents(ComponentDirectory(root), budget, { emptyMap() })
        val ready = assertIs<SemanticComponents.Opening.Ready>(components.open())
        val diagnostics = CopyOnWriteArrayList<String>()
        try {
            val result = ready.provider.embedQuery("text", deadline, diagnostics::add)
            assertContentEquals(
                floatArrayOf(1f, 4f),
                assertIs<WorkerEmbeddingProvider.QueryResult.Embedded>(result).vector,
            )
            assertEquals(8.seconds, deadline.remaining())
            assertEquals(1L, reservations(root))
        } finally {
            ready.close()
        }
        assertEquals(0L, reservations(root))
        assertTrue(diagnostics.contains("Runtime initialized"))
        val handle = ProcessHandle.of(Files.readString(root.resolve("semantic-model/1/model/pid")).toLong())
        handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
        assertTrue(handle.isEmpty || !handle.get().isAlive)
    }

    private fun reservations(root: Path): Long = Files.list(root.resolve("reservations")).use { files ->
        files.filter { it.toString().endsWith(".reservation") }.count()
    }

    @Test
    fun `expiry during admission releases the claim without launching`(@TempDir root: Path) {
        install(root)
        val clock = TestTimeSource()
        val deadline = QueryDeadline(clock)
        val budget = OptionalProcessBudget(root.resolve("reservations"), allowanceMebibytes = {
            clock += 10.seconds
            3000L
        })
        val components = SemanticComponents(ComponentDirectory(root), budget, { emptyMap() })
        val ready = assertIs<SemanticComponents.Opening.Ready>(components.open())
        try {
            val result = assertIs<WorkerEmbeddingProvider.QueryResult.Refused>(
                ready.provider.embedQuery("text", deadline) {},
            )
            assertEquals(WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED, result.reason)
            assertEquals(0L, reservations(root))
            assertTrue(Files.notExists(root.resolve("semantic-model/1/model/pid")))
        } finally {
            ready.close()
        }
    }

    private fun command(root: Path): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("kscope.semantic.test.classpath"),
        FakeEmbeddingMain::class.java.name,
        root.resolve("pid").toString(),
        "request-stderr",
    )

    private fun install(root: Path) {
        val source = root.resolve("Main.java")
        Files.writeString(
            source,
            """
            package io.github.krosstack.kscope.semantic.worker;
            public class Main {
                public static void main(String[] args) throws Exception {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(args[0], "pid"),
                        Long.toString(ProcessHandle.current().pid()));
                    var input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                    input.readLine();
                    System.err.println("Runtime initialized");
                    System.out.println("{\"type\":\"ready\",\"protocolVersion\":1,\"identity\":{\"name\":\"fake\"," +
                        "\"revision\":\"test\",\"dimensions\":2,\"quantization\":\"int8-dynamic\",\"chunkBuilderVersion\":1}}");
                    input.readLine();
                    System.out.println("{\"type\":\"vectors\",\"requestId\":\"1\",\"vectors\":[[1.0,4.0]]}");
                    input.readLine();
                }
            }
            """.trimIndent(),
        )
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", root.toString(), source.toString()) == 0)
        val library = Files.createDirectories(root.resolve("semantic/1/lib"))
        JarOutputStream(Files.newOutputStream(library.resolve("worker.jar"))).use { jar ->
            val resource = "io/github/krosstack/kscope/semantic/worker/Main.class"
            jar.putNextEntry(JarEntry(resource).apply { time = 0 })
            Files.copy(root.resolve(resource), jar)
            jar.closeEntry()
        }
        InstalledComponent.write(InstalledComponent("semantic", "1", library.parent, listOf("lib/worker.jar")))
        val model = Files.createDirectories(root.resolve("semantic-model/1/model"))
        Files.writeString(
            model.resolve("model.json"),
            """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
        )
        InstalledComponent.write(InstalledComponent("semantic-model", "1", model.parent, listOf("model/model.json")))
    }
}

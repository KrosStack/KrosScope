package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.InstalledComponent
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorkerProcessTest {
    @Test
    fun `blocked forwarding retains all three later cause headers`(@TempDir root: Path) {
        val original = System.err
        val forwarding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = object : PrintStream(OutputStream.nullOutputStream()) {
            override fun println(line: String?) {
                forwarding.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        System.setErr(blocked)
        try {
            WorkerProcess(command(root, "many-causes") + "3").use { process ->
                check(forwarding.await(10, TimeUnit.SECONDS))
                val failure = runCatching {
                    process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
                }.exceptionOrNull()
                assertEquals(
                    listOf("Startup failure: runtime") + List(3) { "Caused by: failure $it" },
                    (failure as? IOException)?.message?.substringAfter("\n")?.lines(),
                )
            }
        } finally {
            release.countDown()
            System.setErr(original)
            blocked.close()
        }
    }

    @Test
    fun `an output closure persists the stream failure before benign diagnostics`(@TempDir root: Path) {
        val source = root.resolve("Main.java")
        Files.writeString(
            source,
            """
            package io.github.krosstack.kscope.semantic.worker;
            public class Main {
                public static void main(String[] args) throws Exception {
                    new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                    System.err.println("Runtime initialized");
                    System.out.close();
                }
            }
            """.trimIndent(),
        )
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", root.toString(), source.toString()) == 0)
        val worker = Files.createDirectories(root.resolve("semantic/1"))
        val library = Files.createDirectories(worker.resolve("lib"))
        JarOutputStream(Files.newOutputStream(library.resolve("failure.jar"))).use { jar ->
            val resource = "io/github/krosstack/kscope/semantic/worker/Main.class"
            jar.putNextEntry(JarEntry(resource).apply { time = 0 })
            Files.copy(root.resolve(resource), jar)
            jar.closeEntry()
        }
        val model = Files.createDirectories(worker.parent.parent.resolve("semantic-model/1/model"))
        InstalledComponent.write(InstalledComponent("semantic-model", "1", model.parent, listOf("model/model.json")))
        Files.writeString(
            model.resolve("model.json"),
            """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
        )
        InstalledComponent.write(InstalledComponent("semantic", "1", worker, listOf("bundle.zip")))
        val directory = ComponentDirectory(root)
        WorkerSelfTest(directory).run()
        val reason = requireNotNull(directory.installed("semantic")).problem.orEmpty()
            .removePrefix("${WorkerSelfTest.FAILURE_PREFIX} ")
            .removeSuffix(". ${WorkerSelfTest.JDK_REQUIREMENT}")
        assertTrue(reason.startsWith("Semantic process exited\n") && reason.endsWith("Runtime initialized"))
    }

    @Test
    fun `interrupted exchange keeps interruption after close and stops the process`(@TempDir root: Path) {
        val original = System.err
        val forwarding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val blocked = object : PrintStream(OutputStream.nullOutputStream()) {
            override fun println(line: String?) {
                forwarding.countDown()
                release.await()
            }
        }
        System.setErr(blocked)
        try {
            WorkerProcess(command(root, "silent-cause")).use { process ->
                process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
                check(forwarding.await(10, TimeUnit.SECONDS))
                val exchanging = thread(start = false) {
                    runCatching { process.exchange(EmbeddingRequest.Embed("1", listOf("self-test"), true), 10.seconds) }
                    process.close()
                    interrupted.set(Thread.currentThread().isInterrupted)
                }
                exchanging.start()
                exchanging.interrupt()
                exchanging.join(5000)
                val handle = ProcessHandle.of(Files.readString(root.resolve("pid")).toLong())
                handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
                assertTrue(interrupted.get() && (handle.isEmpty || !handle.get().isAlive))
            }
        } finally {
            release.countDown()
            System.setErr(original)
            blocked.close()
        }
    }

    @Test
    fun `a malformed handshake persists its retained cause`(@TempDir root: Path) {
        val source = root.resolve("Main.java")
        Files.writeString(
            source,
            """
            package io.github.krosstack.kscope.semantic.worker;
            public class Main {
                public static void main(String[] args) throws Exception {
                    new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                    System.err.println("Caused by: IllegalStateException: Missing runtime dependency");
                    System.out.println("malformed");
                    System.in.read();
                }
            }
            """.trimIndent(),
        )
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", root.toString(), source.toString()) == 0)
        val worker = Files.createDirectories(root.resolve("semantic/1"))
        val library = Files.createDirectories(worker.resolve("lib"))
        JarOutputStream(Files.newOutputStream(library.resolve("failure.jar"))).use { jar ->
            val resource = "io/github/krosstack/kscope/semantic/worker/Main.class"
            jar.putNextEntry(JarEntry(resource).apply { time = 0 })
            Files.copy(root.resolve(resource), jar)
            jar.closeEntry()
        }
        val model = Files.createDirectories(worker.parent.parent.resolve("semantic-model/1/model"))
        InstalledComponent.write(InstalledComponent("semantic-model", "1", model.parent, listOf("model/model.json")))
        Files.writeString(
            model.resolve("model.json"),
            """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
        )
        InstalledComponent.write(InstalledComponent("semantic", "1", worker, listOf("bundle.zip")))
        val directory = ComponentDirectory(root)
        WorkerSelfTest(directory).run()
        assertTrue(requireNotNull(directory.installed("semantic")).problem.orEmpty().contains(CAUSE))
    }

    @Test
    fun `thirteen oversized diagnostic headers fit within the diagnostic bound`(@TempDir root: Path) {
        val source = root.resolve("Failure.java")
        Files.writeString(
            source,
            """
            class Failure {
                public static void main(String[] args) {
                    System.err.println("Startup failure: IllegalStateException: " + "x".repeat(4096));
                    for (int i = 0; i < 12; i++) {
                        System.err.println("Caused by: IllegalArgumentException: " + i + "y".repeat(4096));
                    }
                }
            }
            """.trimIndent(),
        )
        val command = listOf(Path.of(System.getProperty("java.home"), "bin", "java").toString(), source.toString())
        WorkerProcess(command).use { process ->
            val failure = runCatching {
                process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
            }.exceptionOrNull()
            val diagnostic = (failure as? IOException)?.message.orEmpty().substringAfter("\n")
            val headers = diagnostic.lines().count {
                it.startsWith("Startup failure: IllegalStateException:") ||
                    it.startsWith("Caused by: IllegalArgumentException:")
            }
            assertTrue(headers == 13 && diagnostic.length <= 2048)
        }
    }

    @Test
    fun `an oversized startup line retains its later cause within the diagnostic bound`(@TempDir root: Path) {
        WorkerProcess(command(root, "long-startup")).use { process ->
            val failure = runCatching {
                process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
            }.exceptionOrNull()
            val diagnostic = failure?.message.orEmpty().substringAfter("\n")
            assertTrue(
                failure is IOException && diagnostic.length <= 2048 &&
                    diagnostic.contains("Startup failure:") &&
                    diagnostic.contains(CAUSE) && diagnostic.endsWith("[stderr truncated]"),
            )
        }
    }

    @Test
    fun `a worker deadline includes its retained startup cause after the deadline text`(@TempDir root: Path) {
        WorkerProcess(command(root, "silent-cause")).use { process ->
            process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
            val failure = runCatching {
                process.exchange(EmbeddingRequest.Embed("1", listOf("self-test"), true), 100.milliseconds)
            }.exceptionOrNull()
            assertTrue(
                failure is IOException && failure.message.orEmpty().startsWith("Semantic process deadline exceeded") &&
                    failure.message.orEmpty().contains("\n$CAUSE"),
            )
        }
    }

    @Test
    fun `startup and twenty cause headers do not consume the ordinary line allowance`(@TempDir root: Path) {
        WorkerProcess(command(root, "many-causes")).use { process ->
            val failure = runCatching {
                process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
            }.exceptionOrNull()
            assertEquals(
                listOf("Startup failure: runtime") + List(20) { "Caused by: failure $it" },
                (failure as? IOException)?.message?.substringAfter("\n")?.lines(),
            )
        }
    }

    @Test
    fun `a deadline closes a noisy worker with retained diagnostics when forwarding stalls`(@TempDir root: Path) {
        val original = System.err
        val forwarding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val blocked = object : PrintStream(OutputStream.nullOutputStream()) {
            override fun println(line: String?) {
                forwarding.countDown()
                release.await()
            }
        }
        System.setErr(blocked)
        try {
            WorkerProcess(command(root, "stderr-forever")).use { process ->
                process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
                check(forwarding.await(10, TimeUnit.SECONDS))
                val pending = executor.submit<Throwable?> {
                    runCatching {
                        process.exchange(EmbeddingRequest.Embed("1", listOf("self-test"), true), 100.milliseconds)
                    }.exceptionOrNull()
                }
                val failure = runCatching { pending.get(4, TimeUnit.SECONDS) }.getOrElse { it }
                assertTrue(failure is IOException && failure.message.orEmpty().contains(CAUSE))
            }
        } finally {
            release.countDown()
            System.setErr(original)
            executor.shutdownNow()
            blocked.close()
        }
    }

    @Test
    fun `diagnostic bounds retain the redaction token without a partial component root`(@TempDir root: Path) {
        val components = Files.createDirectories(root.resolve("p".repeat(96)).resolve("components"))
        val source = root.resolve("Main.java")
        Files.writeString(
            source,
            """
            package io.github.krosstack.kscope.semantic.worker;
            public class Main {
                public static void main(String[] args) {
                    String root = java.nio.file.Path.of(args[0]).getParent().getParent().getParent().toString();
                    System.err.println("Caused by: " + "x".repeat(1960) + root);
                    for (int i = 0; i < 32; i++) System.err.println("at failure " + i + "f".repeat(128));
                }
            }
            """.trimIndent(),
        )
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", root.toString(), source.toString()) == 0)
        val worker = Files.createDirectories(components.resolve("semantic/1"))
        val model = Files.createDirectories(worker.parent.parent.resolve("semantic-model/1/model"))
        InstalledComponent.write(InstalledComponent("semantic-model", "1", model.parent, listOf("model/model.json")))
        Files.writeString(
            model.resolve("model.json"),
            """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
        )
        InstalledComponent.write(InstalledComponent("semantic", "1", worker, listOf("bundle.zip")))
        val library = Files.createDirectories(worker.resolve("lib"))
        JarOutputStream(Files.newOutputStream(library.resolve("failure.jar"))).use { jar ->
            val resource = "io/github/krosstack/kscope/semantic/worker/Main.class"
            jar.putNextEntry(JarEntry(resource).apply { time = 0 })
            Files.copy(root.resolve(resource), jar)
            jar.closeEntry()
        }
        val reason = WorkerSelfTest(ComponentDirectory(components)).run()?.detail.orEmpty()
        val path = components.toString().replace('\\', '/')
        val pathFragments = path.windowed(components.fileName.toString().length + 1)
        val normalizedReason = reason.replace('\\', '/')
        assertTrue(
            reason.contains("<components>") && pathFragments.none { normalizedReason.contains(it) },
        )
    }

    private fun command(root: Path, mode: String): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("kscope.semantic.test.classpath"),
        FakeEmbeddingMain::class.java.name,
        root.resolve("pid").toString(),
        mode,
    )

    private companion object {
        const val CAUSE = "Caused by: IllegalStateException: Missing runtime dependency"
    }
}

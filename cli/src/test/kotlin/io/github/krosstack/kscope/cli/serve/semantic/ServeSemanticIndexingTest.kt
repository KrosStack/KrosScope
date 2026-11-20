package io.github.krosstack.kscope.cli.serve.semantic

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.ServeRequests
import io.github.krosstack.kscope.cli.serve.ServeServices
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.semantic.FakeSemanticService
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import org.junit.jupiter.api.Nested
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeSemanticIndexingTest {
    private class FakeScans(private val fake: FakeSemanticService) : ScanRunner {
        val opened = mutableListOf<OpenOptions>()

        override fun run(workspace: Path, options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult =
            open(workspace, options).use { it.scan(rebuild, verify) }

        override fun open(workspace: Path, options: OpenOptions): KrosScope {
            opened += options
            return fake.open(workspace, options)
        }
    }

    private val foreground = OpenOptions(semanticIndexing = SemanticIndexingMode.FOREGROUND)

    private fun <T> withFake(block: (FakeSemanticService, FakeScans) -> T): T {
        val scratch = Files.createTempDirectory("kscope-fake-components")
        try {
            val fake = FakeSemanticService(scratch)
            return block(fake, FakeScans(fake))
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    private fun generations(root: Path): Long = Files.list(root.resolve(".kscope")).use { files ->
        files.filter { it.fileName.toString().endsWith(".bin") }.count()
    }

    private fun advanceUntil(services: ServeServices, condition: () -> Boolean) {
        var steps = 0
        while (!condition()) {
            check(steps++ < MAXIMUM_STEPS) { "The condition was not reached" }
            services.semantic.advance()
        }
    }

    private fun addFiles(root: Path) = repeat(FILES) {
        writeFile(root, "lib/src/main/kotlin/Added$it.kt", "class Added$it {\n    fun call$it() = $it\n}\n")
    }

    @Test
    fun `ordinary serve advances semantics without watch`() = inWorkspace { root ->
        withFake { fake, scans ->
            withTestServer(root, scanRunner = scans) { server ->
                assertEquals(0, server.query(listOf("scan", "--no-ml")).exitCode)
                assertEquals(0, fake.componentsOpened)
                assertEquals(0, server.query(listOf("scan", "--progress", "never")).exitCode)
                val deadline = System.nanoTime() + TIMEOUT_NANOS
                while (generations(root) == 0L && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
                assertEquals(1L, generations(root))
                assertTrue(fake.textsEmbedded > 0)
            }
        }
    }

    @Test
    fun `watch scan invalidates an in flight batch before reopen`() = inWorkspace { root ->
        withFake { fake, scans ->
            var closedAtScan = -1
            val hooks = ServeServices.ScanHooks(before = { closedAtScan = fake.workersClosed })
            val services = ServeServices(root, scans, hooks = hooks)
            try {
                services.warm()
                services.scan(foreground, rebuild = false, verify = false)
                advanceUntil(services) { fake.workersStarted == 1 }
                assertEquals(0, fake.workersClosed)

                writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nclass Changed\n")
                services.scan(foreground, rebuild = false, verify = false)

                assertEquals(1, closedAtScan)
                assertEquals(0L, generations(root))
                advanceUntil(services) { generations(root) == 1L }
                assertEquals(2, fake.workersStarted)
            } finally {
                services.close()
            }
        }
    }

    @Test
    fun `queries run between completed batches`() = inWorkspace { root ->
        addFiles(root)
        withFake { fake, scans ->
            val services = ServeServices(root, scans)
            try {
                services.warm()
                services.scan(foreground, rebuild = false, verify = false)
                val embedded = mutableListOf<Int>()
                val answers = mutableListOf<Int>()
                advanceUntil(services) {
                    embedded += fake.textsEmbedded
                    answers += services.use { it.info() }.structure.modules
                    generations(root) == 1L
                }
                val growth = embedded.zipWithNext { before, after -> after - before }
                assertTrue(growth.all { it in 0..BATCH }, growth.toString())
                assertTrue(growth.count { it > 0 } >= 2, growth.toString())
                assertEquals(1, answers.distinct().size)
            } finally {
                services.close()
            }
        }
    }

    @Test
    fun `serve startup finishes missing vectors after the first request`() = inWorkspace { root ->
        withFake { fake, scans ->
            fake.open(root, OpenOptions()).use { it.scan() }
            assertEquals(0L, generations(root))
            val services = ServeServices(root, scans)
            try {
                services.use { it.info() }
                advanceUntil(services) { generations(root) == 1L }
                assertTrue(fake.textsEmbedded > 0)
            } finally {
                services.close()
            }
        }
    }

    @Test
    fun `the serve settings reach every open of the workspace`() = inWorkspace { root ->
        withFake { fake, scans ->
            var memory: Long? = 1_024
            val services = ServeServices(root, scans, settings = ServeSettings(workerMemoryMib = { memory }))
            try {
                services.warm()
                memory = 2_048
                services.release()

                assertEquals(listOf<Long?>(1_024, 2_048), scans.opened.map { it.workerMemoryMib })
                assertTrue(scans.opened.all { it.semanticIndexing == SemanticIndexingMode.DEFERRED })
            } finally {
                services.close()
            }
            val off = ServeRequests(root, {}, scans, {}, ServeSettings(ml = MlMode.OFF))
            try {
                off.service.use { it.info() }
                assertEquals(0, off.update())
                repeat(MAXIMUM_STEPS) { off.service.semantic.advance() }
            } finally {
                off.close()
            }
            assertTrue(scans.opened.drop(2).all { it.semanticIndexing == SemanticIndexingMode.OFF })
            assertEquals(0, fake.componentsOpened)
        }
    }

    @Test
    fun `release closes the worker and prevents late publication`() = inWorkspace { root ->
        addFiles(root)
        withFake { fake, scans ->
            val services = ServeServices(root, scans)
            try {
                services.warm()
                services.scan(foreground, rebuild = false, verify = false)
                advanceUntil(services) { fake.workersStarted == 1 }

                services.release()
                repeat(MAXIMUM_STEPS) { services.semantic.advance() }

                assertEquals(1, fake.workersClosed)
                assertEquals(0L, generations(root))
                assertFalse(services.loaded)
            } finally {
                services.close()
            }
        }
    }

    @Nested
    inner class Maintenance {
        @Test
        fun `loaded OFF services still receive semantic resource maintenance`() = inWorkspace { root ->
            withFake { fake, _ ->
                fake.open(root, OpenOptions()).use { scope ->
                    var maintenance = 0
                    val semantics = ServeSemantics({
                        maintenance++
                        scope
                    }, { true }, MlMode.OFF) { null }
                    semantics.advance()
                    assertEquals(1, maintenance)
                    assertEquals(0, fake.componentsOpened)
                    assertEquals(0, fake.queriesEmbedded)
                }
            }
        }
    }

    private companion object {
        const val FILES = 40
        const val BATCH = 16
        const val MAXIMUM_STEPS = 200
        const val POLL_MILLIS = 50L
        const val TIMEOUT_NANOS = 60_000_000_000L
    }
}

package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.isolatedExecution
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.semantic.FakeSemanticService
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ScanSemanticIndexingTest {
    private val unchanged = ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), "")

    private fun scan(root: Path, home: Path, runner: ScanRunner, vararg arguments: String): Int {
        val command = KscopeCommand(execution = isolatedExecution(root, runner))
        val args = listOf("scan", "--progress", "never") + arguments
        return command.test(args, envvars = mapOf(ServiceDirectory.VARIABLE to home.toString())) {
            command.parseWithExitCodes(it)
        }.statusCode
    }

    private fun generations(root: Path): Long = Files.list(root.resolve(".kscope")).use { files ->
        files.filter { it.fileName.toString().endsWith(".bin") }.count()
    }

    private fun <T> withScratch(block: (Path) -> T): T {
        val scratch = Files.createTempDirectory("kscope-scan-semantic")
        try {
            return block(scratch)
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    @Test
    fun `ml off opens no components and ml on indexes in the foreground`() = inWorkspace { root ->
        withScratch { scratch ->
            val fake = FakeSemanticService(scratch.resolve("components"))
            val runner = ScanRunner { workspace, options, rebuild, verify ->
                fake.open(workspace, options).use { it.scan(rebuild, verify) }
            }

            assertEquals(0, scan(root, scratch, runner, "--no-ml"))
            assertEquals(0, scan(root, scratch, runner, "--ml", "off", "--rebuild"))
            assertEquals(0, fake.componentsOpened)
            assertEquals(0, fake.textsEmbedded)
            assertEquals(0L, generations(root))

            assertEquals(0, scan(root, scratch, runner))
            assertEquals(1L, generations(root))
            assertEquals(1, fake.workersStarted)
            assertEquals(1, fake.workersClosed)
        }
    }

    @Test
    fun `the root ml flags and the service file reach the scan options`() = inWorkspace { root ->
        withScratch { scratch ->
            Files.writeString(scratch.resolve("service.toml"), "worker-memory-mib = 3072\n")
            val seen = mutableListOf<OpenOptions>()
            val runner = ScanRunner { _, options, _, _ ->
                seen += options
                unchanged
            }

            scan(root, scratch, runner)
            scan(root, scratch, runner, "--ml", "on")
            scan(root, scratch, runner, "--ml", "off")
            scan(root, scratch, runner, "--ml", "on", "--no-ml")

            val modes = seen.map { it.semanticIndexing }
            val foreground = SemanticIndexingMode.FOREGROUND
            val off = SemanticIndexingMode.OFF
            assertEquals(listOf(foreground, foreground, off, off), modes)
            assertEquals(listOf<Long?>(3_072, 3_072, null, null), seen.map { it.workerMemoryMib })
        }
    }
}

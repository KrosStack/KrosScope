package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.doctor
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerSelfTestLifecycleCommandTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `doctor clears the manifest worker failure without touching the older version`() {
        val fixture = failedInstallation()
        val manifest = fixture.manifest.copy(components = listOf(fixture.worker.copy(version = "2"), fixture.model))
        val old = requireNotNull(fixture.directory.installed(fixture.worker.name)).directory
        val before = Files.walk(old).use { paths ->
            paths.filter(Files::isRegularFile).sorted().map { it to Files.readAllBytes(it).toList() }.toList()
        }
        ComponentsCommand(manifest, fixture.directory).test(
            listOf("install", fixture.worker.name, "--yes", "--from", root.resolve("source").toString()),
        )
        val current = fixture.directory.installed().single { it.name == fixture.worker.name && it.version == "2" }
        check(current.problem?.startsWith("Self-test failed:") == true)
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.writeString(model.directory.resolve("model/mode"), "normal")
        KrosScope.open(root).use { it.doctor(fixture.directory, manifest) }
        val after = Files.walk(old).use { paths ->
            paths.filter(Files::isRegularFile).sorted().map { it to Files.readAllBytes(it).toList() }.toList()
        }
        assertTrue(!Files.exists(current.directory.resolve("kscope-self-test.txt")) && before == after)
    }

    @Test
    fun `generic inference failures persist their detailed stderr reason`() {
        val fixture = SemanticInstallFixture(root, "inference-failed")
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        assertTrue(
            fixture.directory.installed(fixture.worker.name)?.problem.orEmpty()
                .contains("ONNX inference failed\nCaused by: IllegalStateException: Missing runtime dependency"),
        )
    }

    @Test
    fun `a failed self-test does not prevent component removal`() {
        val fixture = failedInstallation()
        assertEquals(0, fixture.command().test(listOf("remove", fixture.worker.name)).statusCode)
    }

    @Test
    fun `a failed self-test does not prevent replacement`() {
        val fixture = failedInstallation()
        assertTrue(fixture.install(fixture.worker.name).stdout.contains("Replaced semantic 1. Self-test failed:"))
    }

    @Test
    fun `a worker that handshakes must also answer an embedding`() {
        val fixture = SemanticInstallFixture(root, "bad")
        fixture.install(fixture.worker.name)
        val result = fixture.install(fixture.model.name)
        assertTrue(result.stdout.contains("Unexpected semantic response"))
    }

    @Test
    fun `install tests the new worker version when an older one remains installed`() {
        val fixture = failedInstallation()
        val manifest = fixture.manifest.copy(components = listOf(fixture.worker.copy(version = "2"), fixture.model))
        ComponentsCommand(manifest, fixture.directory).test(
            listOf("install", fixture.worker.name, "--yes", "--from", root.resolve("source").toString()),
        )
        assertTrue(fixture.directory.installed().single { it.version == "2" }.problem.orEmpty().contains(REASON))
    }

    @Test
    fun `replacement reports a failed self-test`() {
        val fixture = failedInstallation()
        assertEquals(1, fixture.install(fixture.worker.name).statusCode)
    }

    @Test
    fun `a worker that handshakes but fails embedding exits one`() {
        val fixture = SemanticInstallFixture(root, "bad")
        fixture.install(fixture.worker.name)
        assertEquals(1, fixture.install(fixture.model.name).statusCode)
    }

    private fun failedInstallation(): SemanticInstallFixture = SemanticInstallFixture(root, "exit").also {
        it.install(it.worker.name)
        it.install(it.model.name)
    }

    private companion object {
        const val REASON = "Native runtime could not initialize"
    }
}

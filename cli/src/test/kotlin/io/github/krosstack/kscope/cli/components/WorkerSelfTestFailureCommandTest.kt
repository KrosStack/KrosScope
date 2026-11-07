package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerSelfTestFailureCommandTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `failed self-test exits one`() {
        val fixture = SemanticInstallFixture(root, "exit")
        fixture.install(fixture.worker.name)
        assertEquals(1, fixture.install(fixture.model.name).statusCode)
    }

    @Test
    fun `failed self-test retains the worker installation`() {
        val fixture = failedInstallation()
        val worker = requireNotNull(fixture.directory.installed(fixture.worker.name))
        assertTrue(Files.isDirectory(worker.directory.resolve("lib")))
    }

    @Test
    fun `failed self-test retains the model installation`() {
        val fixture = failedInstallation()
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        assertTrue(Files.isRegularFile(model.directory.resolve("model/model.json")))
    }

    @Test
    fun `failure message carries the worker reason`() {
        val fixture = SemanticInstallFixture(root, "exit")
        fixture.install(fixture.worker.name)
        assertTrue(fixture.install(fixture.model.name).stdout.contains(REASON))
    }

    @Test
    fun `failure message names the JDK requirement`() {
        val fixture = SemanticInstallFixture(root, "exit")
        fixture.install(fixture.worker.name)
        assertTrue(
            fixture.install(fixture.model.name).stdout.contains(
                "a JDK 17 whose bundled Microsoft Visual C++ runtime is 14.40 or newer, " +
                    "for example Temurin 17.0.21 or newer",
            ),
        )
    }

    @Test
    fun `status retains the failed self-test as an installed component problem`() {
        val fixture = failedInstallation()
        val result = fixture.command().test(listOf("status"))
        assertTrue(result.stdout.contains("installed; Self-test failed: Semantic process exited\n$REASON"))
    }

    @Test
    fun `list includes the installed self-test failure reason`() {
        val fixture = failedInstallation()
        val result = fixture.command().test(listOf("list", "--workspace", root.toString()))
        assertTrue(result.stdout.contains("installed; Self-test failed: Semantic process exited\n$REASON"))
    }

    @Test
    fun `status shows the missing descriptor field as a self-test problem`() {
        val fixture = failedInstallation()
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.writeString(model.directory.resolve("model/model.json"), """{"revision":"test","dimensions":2}""")
        fixture.install(fixture.worker.name)
        val result = fixture.command().test(listOf("status"))
        assertTrue(result.stdout.contains("Self-test failed: Missing or malformed model field: name"))
    }

    @Test
    fun `list shows the missing descriptor field as a self-test problem`() {
        val fixture = failedInstallation()
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.writeString(model.directory.resolve("model/model.json"), """{"revision":"test","dimensions":2}""")
        fixture.install(fixture.worker.name)
        val result = fixture.command().test(listOf("list", "--workspace", root.toString()))
        assertTrue(result.stdout.contains("Self-test failed: Missing or malformed model field: name"))
    }

    private fun failedInstallation(): SemanticInstallFixture = SemanticInstallFixture(root, "exit").also {
        it.install(it.worker.name)
        it.install(it.model.name)
    }

    private companion object {
        const val REASON = "Native runtime could not initialize"
    }
}

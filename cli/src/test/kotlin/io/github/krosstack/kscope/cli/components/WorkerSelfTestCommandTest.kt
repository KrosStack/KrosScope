package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerSelfTestCommandTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `install reports the worker answered`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        val result = fixture.install(fixture.model.name)
        assertEquals(
            "Installed semantic-model 1. Semantic worker answered.",
            result.stdout.trim().lineSequence().last(),
        )
    }

    @Test
    fun `successful self-test exits zero`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        assertEquals(0, fixture.install(fixture.model.name).statusCode)
    }

    @Test
    fun `self-test stops the worker`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        assertTrue(fixture.stopped())
    }

    @Test
    fun `installing the worker after the model also tests it`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.model.name)
        assertTrue(fixture.install(fixture.worker.name).stdout.contains("Semantic worker answered."))
    }

    @Test
    fun `installing an unrelated component succeeds despite a failing semantic worker`() {
        val fixture = SemanticInstallFixture(root, "exit")
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val unrelated = fixture.worker.copy(name = "unrelated")
        val command = ComponentsCommand(fixture.manifest.copy(components = listOf(unrelated)), fixture.directory)
        val result = command.test(
            listOf("install", unrelated.name, "--yes", "--from", root.resolve("source").toString()),
        )
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `installing an unrelated component does not retry a failed semantic worker`() {
        val fixture = SemanticInstallFixture(root, "exit")
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.writeString(model.directory.resolve("model/mode"), "normal")
        val unrelated = fixture.worker.copy(name = "unrelated")
        ComponentsCommand(fixture.manifest.copy(components = listOf(unrelated)), fixture.directory).test(
            listOf("install", unrelated.name, "--yes", "--from", root.resolve("source").toString()),
        )
        val worker = requireNotNull(fixture.directory.installed(fixture.worker.name))
        assertTrue(worker.problem.orEmpty().contains("Self-test failed:"))
    }
}

package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.command.DoctorCommand
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class WorkerSelfTestPayloadCommandTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `an installed pair missing model json exits one`() {
        val fixture = missingModelDescriptor()
        assertEquals(1, fixture.install(fixture.worker.name).statusCode)
    }

    @Test
    fun `a missing model descriptor is named in the install message`() {
        val fixture = missingModelDescriptor()
        assertContains(
            fixture.install(fixture.worker.name).stdout,
            "Self-test failed: Missing or unreadable model.json",
        )
    }

    @Test
    fun `a missing model descriptor is named in the saved problem`() {
        val fixture = missingModelDescriptor()
        fixture.install(fixture.worker.name)
        val problem = requireNotNull(fixture.directory.installed(fixture.worker.name)).problem.orEmpty()
        assertContains(problem, "Self-test failed: Missing or unreadable model.json")
    }

    @Test
    fun `status shows the saved missing model descriptor problem`() {
        val fixture = missingModelDescriptor()
        fixture.install(fixture.worker.name)
        val result = fixture.command().test(listOf("status"))
        assertContains(result.stdout, "Self-test failed: Missing or unreadable model.json")
    }

    @Test
    fun `list shows the missing model descriptor problem`() {
        val fixture = missingModelDescriptor()
        fixture.install(fixture.worker.name)
        val result = fixture.command().test(listOf("list", "--workspace", root.toString()))
        assertContains(result.stdout, "Self-test failed: Missing or unreadable model.json")
    }

    @Test
    fun `doctor reports the missing model descriptor`() {
        val fixture = missingModelDescriptor()
        fixture.install(fixture.worker.name)
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertContains(result.stdout, "Self-test failed: Missing or unreadable model.json")
    }

    @Test
    fun `multiline startup diagnostics retain the startup message in the saved problem`() {
        val fixture = SemanticInstallFixture(root, "multiline")
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val problem = requireNotNull(fixture.directory.installed(fixture.worker.name)).problem.orEmpty()
        assertContains(problem, "IllegalStateException: Native startup failed")
    }

    @Test
    fun `multiline startup diagnostics retain a late cause in the saved problem`() {
        val fixture = SemanticInstallFixture(root, "multiline")
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val problem = requireNotNull(fixture.directory.installed(fixture.worker.name)).problem.orEmpty()
        assertContains(problem, "\nCaused by: IllegalArgumentException: Missing runtime dependency")
    }

    @Test
    fun `multiline startup diagnostics note omitted frames in the saved problem`() {
        val fixture = SemanticInstallFixture(root, "multiline")
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val problem = requireNotNull(fixture.directory.installed(fixture.worker.name)).problem.orEmpty()
        assertContains(problem, "[stderr truncated]")
    }

    private fun missingModelDescriptor(): SemanticInstallFixture = SemanticInstallFixture(root).also { fixture ->
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.delete(model.directory.resolve("model/model.json"))
    }
}

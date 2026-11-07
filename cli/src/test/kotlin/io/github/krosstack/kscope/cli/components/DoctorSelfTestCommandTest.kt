package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.command.DoctorCommand
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.service.components.InstalledComponent
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoctorSelfTestCommandTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `doctor succeeds after a successful self-test`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `doctor prints the running JDK vendor`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertTrue(result.stdout.contains(System.getProperty("java.vendor")))
    }

    @Test
    fun `doctor prints the full running JDK version`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertTrue(result.stdout.contains(System.getProperty("java.version")))
    }

    @Test
    fun `doctor reports that the worker answered`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertTrue(result.stdout.contains("Semantic worker answered."))
    }

    @Test
    fun `doctor keeps exit zero when the worker fails`() {
        val fixture = failedInstallation()
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString(), "--format", "json"))
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `doctor retries a failing worker and prints its reason`() {
        val fixture = failedInstallation()
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString(), "--format", "json"))
        assertTrue(result.stdout.contains(REASON))
    }

    @Test
    fun `doctor names the JDK requirement when the worker fails`() {
        val fixture = failedInstallation()
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString(), "--format", "json"))
        assertTrue(result.stdout.contains(JDK_REQUIREMENT))
    }

    @Test
    fun `doctor JSON redacts the installation path from a model payload failure`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.delete(model.directory.resolve("model/mode"))
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString(), "--format", "json"))
        val detail = parseJson(result.stdout).getValue("checks").jsonArray.map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == "semantic-worker" }
            .getValue("detail").jsonPrimitive.content
        val installation = fixture.directory.root.toAbsolutePath().toString().replace('\\', '/')
        assertTrue(
            detail.startsWith("Self-test failed:") && detail.contains("<components>") &&
                !detail.contains(installation) && !detail.contains(installation.replace('/', '\\')),
        )
    }

    @Test
    fun `a successful retry clears the saved failure`() {
        val fixture = failedInstallation()
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        Files.writeString(model.directory.resolve("model/mode"), "normal")
        DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertEquals(null, requireNotNull(fixture.directory.installed(fixture.worker.name)).problem)
    }

    @Test
    fun `doctor skips a bundled model when the manifest model component is missing`() {
        val fixture = SemanticInstallFixture(root)
        fixture.install(fixture.worker.name)
        fixture.install(fixture.model.name)
        val model = requireNotNull(fixture.directory.installed(fixture.model.name))
        val worker = requireNotNull(fixture.directory.installed(fixture.worker.name))
        Files.move(
            model.directory.resolve("model"),
            worker.directory.resolve("model"),
        )
        fixture.directory.remove(fixture.model.name)
        val other = Files.createDirectories(root.resolve("home/aaa-model/1"))
        Files.createDirectory(other.resolve("model"))
        Files.copy(worker.directory.resolve("model/model.json"), other.resolve("model/model.json"))
        Files.writeString(other.resolve("model/mode"), "exit")
        InstalledComponent.write(InstalledComponent("aaa-model", "1", other, listOf("model/model.json")))
        val result = DoctorCommand(fixture.directory).test(listOf("--workspace", root.toString()))
        assertTrue(!result.stdout.contains("semantic-worker"))
    }

    private fun failedInstallation(): SemanticInstallFixture = SemanticInstallFixture(root, "exit").also {
        it.install(it.worker.name)
        it.install(it.model.name)
    }

    private companion object {
        const val REASON = "Native runtime could not initialize"
        const val JDK_REQUIREMENT = "a JDK 17 whose bundled Microsoft Visual C++ runtime is 14.40 or newer, " +
            "for example Temurin 17.0.21 or newer"
    }
}

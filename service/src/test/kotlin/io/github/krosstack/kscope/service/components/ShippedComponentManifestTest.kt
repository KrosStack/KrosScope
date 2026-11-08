package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.semantic.WorkerSelfTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShippedComponentManifestTest {
    private val manifest = ComponentManifest.read(Files.readString(Path.of("src/main/resources/components.json")))

    @Test
    fun `shipped manifest separates semantic runtime and model files`() {
        val components = manifest.components.associate { component ->
            component.name to component.files.map { it.path }
        }
        assertEquals(
            mapOf(
                "semantic" to listOf(
                    "semantic-worker-0.1.0.zip",
                ),
                "semantic-model" to listOf(
                    "model/LICENSE",
                    "model/model.json",
                    "model/model.onnx",
                    "model/tokenizer.json",
                ),
            ),
            components,
        )
    }

    @Test
    fun `shipped runtime declares its archive licence`() {
        val runtime = manifest.components.single { it.name == "semantic" }
        assertEquals("Apache-2.0" to "LICENSE", runtime.licence to runtime.licenceFile)
    }

    @Test
    fun `shipped model declares its own licence`() {
        val model = manifest.components.single { it.name == "semantic-model" }
        assertEquals("MIT" to "model/LICENSE", model.licence to model.licenceFile)
    }

    @Test
    fun `shipped semantic components are discoverable by the worker self-test`(@TempDir root: Path) {
        manifest.components.forEach { entry ->
            val target = Files.createDirectories(root.resolve(entry.name).resolve(entry.version))
            entry.files.filter { it.path.startsWith("model/") }.forEach { file ->
                val payload = target.resolve(file.path)
                Files.createDirectories(payload.parent)
                Files.writeString(payload, "")
            }
            InstalledComponent.write(InstalledComponent(entry.name, entry.version, target, entry.files.map { it.path }))
        }
        assertNotNull(WorkerSelfTest(ComponentDirectory(root), manifest).run())
    }
}

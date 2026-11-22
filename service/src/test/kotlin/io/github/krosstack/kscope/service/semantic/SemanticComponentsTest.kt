package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.InstalledComponent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SemanticComponentsTest {
    @Test
    fun `the reservation is released when the worker cannot start`(@TempDir root: Path) {
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        val descriptor = """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8"}"""
        install(root, "semantic-model", mapOf("model/model.json" to descriptor))
        val reservations = root.resolve("reservations")
        val components = SemanticComponents(
            ComponentDirectory(root),
            OptionalProcessBudget(reservations),
            { emptyMap() },
        )
        val ready = assertIs<SemanticComponents.Opening.Ready>(components.open())
        Files.delete(root.resolve("semantic/1/lib/placeholder"))
        Files.delete(root.resolve("semantic/1/lib"))
        assertFailsWith<IllegalArgumentException> { ready.provider.cap("text", 1) }
        val held = Files.list(reservations).use { files ->
            files.filter { it.toString().endsWith(".reservation") }.count()
        }
        assertEquals(0L, held)
        ready.close()
    }

    private fun install(root: Path, name: String, files: Map<String, String>) {
        val target = Files.createDirectories(root.resolve("$name/1"))
        files.forEach { (path, content) ->
            val file = target.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        InstalledComponent.write(InstalledComponent(name, "1", target, files.keys.toList()))
    }
}

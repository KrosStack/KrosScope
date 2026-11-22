package io.github.krosstack.kscope.service.diagram

import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiagramUseCaseTest {
    private fun <T> scanned(block: (KrosScope) -> T): T {
        val workspace = Files.createTempDirectory("kscope-diagram-api")
        try {
            write(workspace, "settings.gradle.kts", "include(\":lib\")\n")
            write(workspace, "lib/build.gradle.kts", "")
            write(workspace, "lib/src/main/kotlin/Lib.kt", "class Lib\n")
            return KrosScope.open(workspace).use { scope ->
                scope.scan()
                block(scope)
            }
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    private fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    @Test
    fun `a module that the index does not hold is not found, by path and by id`() {
        scanned { scope ->
            val byPath = scope.diagram(DiagramRequest(DiagramKind.MODULES, ":absent"))
            val byId = scope.diagram(DiagramRequest(DiagramKind.MODULES, "module:absent"))

            assertEquals(DiagramResult.NotFound(":absent"), byPath)
            assertEquals(DiagramResult.NotFound("module:absent"), byId)
        }
    }

    @Test
    fun `a repository that the index does not hold is not found`() {
        scanned { scope ->
            val result = scope.diagram(DiagramRequest(DiagramKind.COMPONENTS, "absent"))

            assertEquals(DiagramResult.NotFound("absent"), result)
        }
    }

    @Test
    fun `a module and a repository that the index holds are drawn, by every name that picks them`() {
        scanned { scope ->
            val structure = scope.graph().structure()
            val module = structure.modules.single { it.path == ":lib" }
            val repository = structure.repositories.single()

            val requests = listOf(
                DiagramRequest(DiagramKind.MODULES, ":lib"),
                DiagramRequest(DiagramKind.MODULES, module.id.value),
                DiagramRequest(DiagramKind.COMPONENTS, repository.name),
                DiagramRequest(DiagramKind.COMPONENTS, repository.id.value),
                DiagramRequest(DiagramKind.MODULES),
            )
            requests.forEach { assertIs<DiagramResult.Built>(scope.diagram(it), it.toString()) }
        }
    }
}

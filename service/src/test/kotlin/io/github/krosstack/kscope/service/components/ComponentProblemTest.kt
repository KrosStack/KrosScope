package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.semantic.WorkerSelfTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNull

class ComponentProblemTest {
    @Test
    fun `a problem marker removed between check and read means no problem`(@TempDir root: Path) {
        val target = Files.createDirectories(root.resolve("semantic/1"))
        val component = InstalledComponent("semantic", "1", target, emptyList())
        Files.writeString(target.resolve(WorkerSelfTest.PROBLEM_FILE), "Self-test failed")
        val read = ComponentDirectory(root).withSelfTestProblem(component) { marker ->
            Files.delete(marker)
            Files.readString(marker)
        }
        assertNull(read.problem)
    }
}

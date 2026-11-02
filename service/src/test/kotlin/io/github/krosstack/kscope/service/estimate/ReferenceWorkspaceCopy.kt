package io.github.krosstack.kscope.service.estimate

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path

/** The reference workspace without its build output, copied to a temporary directory so that a scan can write to it. */
internal class ReferenceWorkspaceCopy(fixture: Path) : AutoCloseable {
    val root: Path = Files.createTempDirectory("kscope-reference-estimates")

    init {
        Files.walk(fixture).use { paths ->
            paths.filter { path -> fixture.relativize(path).none { it.toString() in DERIVED } }.forEach { source ->
                val target = root.resolve(fixture.relativize(source).toString())
                if (Files.isDirectory(source)) Files.createDirectories(target) else Files.copy(source, target)
            }
        }
    }

    override fun close() {
        TemporaryTree.delete(root)
    }

    private companion object {
        val DERIVED = setOf(".gradle", ".kotlin", ".kscope", "build")
    }
}

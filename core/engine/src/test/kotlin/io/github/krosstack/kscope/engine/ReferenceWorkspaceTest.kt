package io.github.krosstack.kscope.engine

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs against `fixtures/reference-workspace` when a complete copy is present and is skipped otherwise. */
class ReferenceWorkspaceTest {
    private val includePattern = Regex("""include\("(:[^"]+)"\)""")

    private fun locate(): Path? = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("fixtures").resolve("reference-workspace") }
        .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }

    private fun includedModules(root: Path): List<String> =
        includePattern.findAll(Files.readString(root.resolve("settings.gradle.kts"))).map { it.groupValues[1] }.toList()

    private fun isComplete(root: Path, modules: List<String>): Boolean = modules.isNotEmpty() && modules.all { path ->
        val directory = root.resolve(path.trim(':').replace(':', '/'))
        listOf("build.gradle.kts", "build.gradle").any { Files.isRegularFile(directory.resolve(it)) }
    }

    private fun scanTwice(root: Path, block: (ScanEngine, UpdateReport) -> Unit) {
        val index = Files.createTempDirectory("kscope-reference")
        try {
            val config = WorkspaceConfig(root = root, indexDirectory = index)
            ScanEngine(coreRegistry(), config).use { engine ->
                engine.update()
                block(engine, engine.update(verify = true))
            }
        } finally {
            index.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the reference workspace scans into a model with all its modules and an exact second update`() {
        val root = locate()
        assumeTrue(root != null, "fixtures/reference-workspace is not present")
        requireNotNull(root)
        val modules = includedModules(root)
        assumeTrue(isComplete(root, modules), "fixtures/reference-workspace is incomplete")
        scanTwice(root) { engine, second ->
            val modulePaths = engine.graph().structure().modules.map { it.path }
            assertTrue(modulePaths.containsAll(modules), "Missing modules: ${modules - modulePaths.toSet()}")
            assertTrue(engine.graph().structure().sourceSets.isNotEmpty())
            assertEquals(0, second.unitsProduced)
            assertTrue(assertNotNull(second.verify).matches)
        }
    }
}

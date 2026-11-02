package io.github.krosstack.kscope.cli

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Starts the generated launcher of the installed distribution from directories whose names contain spaces. */
class LauncherPathsTest {
    private val windows = System.getProperty("os.name").lowercase().contains("windows")

    private fun installedDistribution(): Path? =
        System.getProperty("kscope.distribution")?.let(Path::of)?.takeIf { Files.isDirectory(it.resolve("lib")) }

    private fun copyTree(from: Path, to: Path) {
        Files.walk(from).use { paths ->
            paths.forEach { source ->
                val target = to.resolve(from.relativize(source).toString())
                if (Files.isDirectory(source)) Files.createDirectories(target) else Files.copy(source, target)
            }
        }
    }

    private fun run(launcher: Path, vararg arguments: String): Pair<Int, String> {
        val command = listOf(launcher.toString()) + arguments
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        val process = builder.start()
        val output = process.inputStream.readAllBytes().decodeToString()
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "The launcher did not finish: $output" }
        return process.exitValue() to output
    }

    @Test
    fun `the launcher scans and answers from an install directory and a workspace with spaces in their paths`() {
        val distribution = installedDistribution()
        assumeTrue(distribution != null, "the installed distribution is not present")
        val base = Files.createTempDirectory("kscope launcher ")
        try {
            val installed = base.resolve("install dir").resolve("kscope")
            Files.createDirectories(installed)
            copyTree(requireNotNull(distribution), installed)
            val workspace = base.resolve("work space")
            writeFile(workspace, "settings.gradle.kts", "include(\":lib\")\n")
            writeFile(workspace, "lib/build.gradle.kts", "")
            writeFile(workspace, "lib/src/main/kotlin/Lib.kt", "class Lib { fun greet() = 1 }\n")
            val launcher = installed.resolve("bin").resolve(if (windows) "kscope.bat" else "kscope")

            val scan = run(launcher, "--workspace", workspace.toString(), "scan", "--quiet")
            assertEquals(0, scan.first, scan.second)
            assertTrue(Files.isRegularFile(workspace.resolve(".kscope/index.db")), scan.second)

            val analysis = run(launcher, "--workspace", workspace.toString(), "analyze", "greet a person", "--quiet")
            assertEquals(0, analysis.first, analysis.second)
            assertTrue(analysis.second.contains("Lib.kt"), analysis.second)
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 300L
    }
}

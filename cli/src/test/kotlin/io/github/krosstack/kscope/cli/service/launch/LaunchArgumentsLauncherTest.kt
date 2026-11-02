package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.service.entry.Installation
import io.github.krosstack.kscope.cli.service.entry.LaunchFile
import io.github.krosstack.kscope.cli.service.entry.inTemporaryDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaunchArgumentsLauncherTest {
    private val java: String = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    private val values = listOf(
        "a b",
        "c\\d\\",
        "say \"hi\"",
        "it's",
        "#not a comment",
        "",
        "tab\there",
        "two\nlines",
        "-DProgram=C:\\Program Files\\Java",
    )

    private fun copyClassInto(type: Class<*>, root: Path) {
        val entry = type.name.replace('.', '/') + ".class"
        val target = root.resolve(entry)
        Files.createDirectories(target.parent)
        val bytes = checkNotNull(type.getResourceAsStream("/$entry")) { "$entry is not on the class path" }
        bytes.use { Files.copy(it, target) }
    }

    @Test
    fun `the java launcher reads back the arguments of a launch from directories that contain spaces`() =
        inTemporaryDirectory("with space") { home ->
            val classes = home.resolve("my classes")
            copyClassInto(ArgumentEchoMain::class.java, classes)
            val stdlib = Path.of(Unit::class.java.protectionDomain.codeSource.location.toURI())
            val echoed = Files.createDirectories(home.resolve("out dir")).resolve("echo.txt")
            val classPath = listOf(classes, stdlib).joinToString(File.pathSeparator)
            val options = listOf("-D${ArgumentEchoMain.OUTPUT_PROPERTY}=$echoed", "-cp", classPath)
            val launch = ServiceLaunch(Path.of(java), options + ArgumentEchoMain::class.java.name + values)
            val file = LaunchFile(home.resolve("service dir").resolve("launch.args"))
            file.installing(launch) { Installation.Installed }
            val output = home.resolve("java-output.txt")

            val process = ProcessBuilder(java, "@${file.path}").redirectErrorStream(true)
                .redirectOutput(output.toFile()).start()
            try {
                assertTrue(process.waitFor(PROCESS_SECONDS, TimeUnit.SECONDS), "java did not end")
                assertEquals(0, process.exitValue(), Files.readString(output))
            } finally {
                process.destroyForcibly()
            }

            assertEquals(values, Files.readString(echoed).split(ArgumentEchoMain.SEPARATOR))
        }

    private companion object {
        const val PROCESS_SECONDS = 120L
    }
}

package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.service.entry.inTemporaryDirectory
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmProcessTest {
    private fun jvm(command: String?, javaHome: String, osName: String) =
        JvmProcess.of(command, javaHome, osName, "lib", listOf("-Dx=1"))

    @Test
    fun `on Windows the program without a console from the same directory is used when it exists`() =
        inTemporaryDirectory { bin ->
            val java = Files.createFile(bin.resolve("java.exe"))
            val javaw = Files.createFile(bin.resolve("javaw.exe"))

            val jvm = jvm(java.toString(), "unused", "Windows 11")

            assertEquals(javaw, jvm.executable)
            assertEquals("lib", jvm.classPath)
            assertEquals(listOf("-Dx=1"), jvm.options)
        }

    @Test
    fun `on Windows java is used when there is no program without a console`() = inTemporaryDirectory { bin ->
        val java = Files.createFile(bin.resolve("java.exe"))

        assertEquals(java, jvm(java.toString(), "unused", "Windows Server 2022").executable)
    }

    @Test
    fun `on other systems the java of this process is used even if a javaw exists`() = inTemporaryDirectory { bin ->
        val java = Files.createFile(bin.resolve("java"))
        Files.createFile(bin.resolve("javaw.exe"))

        assertEquals(java, jvm(java.toString(), "unused", "Linux").executable)
    }

    @Test
    fun `without a command the java below java home is used`() = inTemporaryDirectory { home ->
        assertEquals(Path.of(home.toString(), "bin", "java"), jvm(null, home.toString(), "Linux").executable)
        assertEquals(Path.of(home.toString(), "bin", "java.exe"), jvm(null, home.toString(), "Windows 10").executable)
    }

    @Test
    fun `this process is described by its own executable, class path and options`() {
        val current = JvmProcess.current()

        assertTrue(Files.exists(current.executable), current.executable.toString())
        assertEquals(System.getProperty("java.class.path"), current.classPath)
        assertEquals(ManagementFactory.getRuntimeMXBean().inputArguments, current.options)
    }
}

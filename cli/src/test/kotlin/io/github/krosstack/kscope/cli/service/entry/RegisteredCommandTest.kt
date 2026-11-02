package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegisteredCommandTest {
    private class Installed(val java: Path, val argumentFile: Path, val jars: List<Path>) {
        fun registered() = RegisteredCommand(java.toString(), argumentFile.toString())
    }

    private fun <T> installed(block: (Installed) -> T): T = inTemporaryDirectory { home ->
        val java = Files.createFile(home.resolve("java"))
        val jars = listOf("a.jar", "b.jar").map { Files.createFile(home.resolve(it)) }
        val argumentFile = home.resolve("launch.args")
        val classPath = jars.joinToString(File.pathSeparator)
        LaunchFile(argumentFile).installing(ServiceLaunch(java, listOf("-cp", classPath, "Main"))) {
            Installation.Installed
        }
        block(Installed(java, argumentFile, jars))
    }

    @Test
    fun `nothing is missing when the java, the argument file and the class path exist`() = installed { entry ->
        assertNull(entry.registered().missing())
    }

    @Test
    fun `a missing java executable is named`() = installed { entry ->
        Files.delete(entry.java)

        assertEquals("the Java executable ${entry.java}", entry.registered().missing())
    }

    @Test
    fun `a missing argument file is named`() = installed { entry ->
        Files.delete(entry.argumentFile)

        assertEquals("the argument file ${entry.argumentFile}", entry.registered().missing())
    }

    @Test
    fun `a class path entry that no longer exists is named, which is what moving kscope leaves behind`() =
        installed { entry ->
            Files.delete(entry.jars[1])

            assertEquals("the class path entry ${entry.jars[1]}", entry.registered().missing())
        }

    @Test
    fun `a wildcard in the class path stands for its directory`() = installed { entry ->
        val directory = Files.createDirectory(entry.argumentFile.resolveSibling("lib"))
        val wildcard = directory.toString() + File.separator + "*"
        val file = LaunchFile(entry.argumentFile)
        file.installing(ServiceLaunch(entry.java, listOf("-cp", wildcard, "Main"))) { Installation.Installed }

        assertNull(entry.registered().missing())
        Files.delete(directory)
        assertEquals("the class path entry $wildcard", entry.registered().missing())
    }

    @Test
    fun `a path that could not be decoded is not reported as missing`() = installed { entry ->
        val garbled = RegisteredCommand("C:\\Users\\Jos\uFFFD\\javaw.exe", entry.argumentFile.toString())

        assertNull(garbled.missing())
    }

    @Test
    fun `only a java executable followed by an argument file is a command of an install`() {
        assertNotNull(RegisteredCommand.of(listOf("java", "@launch.args")))
        assertNull(RegisteredCommand.of(listOf("java")))
        assertNull(RegisteredCommand.of(listOf("java", "launch.args")))
        assertNull(RegisteredCommand.of(listOf("java", "@launch.args", "extra")))
        assertEquals("launch.args", RegisteredCommand.of(listOf("java", "@launch.args"))?.argumentFile)
    }
}

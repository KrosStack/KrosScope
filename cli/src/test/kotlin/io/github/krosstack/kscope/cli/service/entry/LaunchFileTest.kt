package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LaunchFileTest {
    private val launch = ServiceLaunch(Path.of("java"), listOf("-cp", "a${File.pathSeparator}b", "Main"))
    private val other = ServiceLaunch(Path.of("java"), listOf("-cp", "other", "Main"))

    @Test
    fun `the file is staged beside its place during the registration and moved there when that succeeds`() =
        inTemporaryDirectory { home ->
            val file = LaunchFile(home.resolve("service").resolve("launch.args"))
            var namesDuringRegistration = emptyList<String>()

            val result = file.installing(launch) {
                namesDuringRegistration = namesIn(home.resolve("service"))
                Installation.Installed
            }

            assertEquals(Installation.Installed, result)
            assertEquals(listOf("launch.args.tmp"), namesDuringRegistration)
            assertEquals(listOf("launch.args"), namesIn(home.resolve("service")))
            assertEquals(listOf("a", "b"), file.classPath())
        }

    @Test
    fun `a first install that is refused or fails leaves no file`() = inTemporaryDirectory { home ->
        val file = LaunchFile(home.resolve("launch.args"))

        val refused = file.installing(launch) { Installation.Refused("no") }
        assertFailsWith<IOException> { file.installing(launch) { throw IOException("disk") } }

        assertEquals(Installation.Refused("no"), refused)
        assertEquals(emptyList(), namesIn(home))
    }

    @Test
    fun `a reinstall that is refused or fails keeps the previous file`() = inTemporaryDirectory { home ->
        val file = LaunchFile(home.resolve("launch.args"))
        file.installing(launch) { Installation.Installed }
        val before = Files.readString(file.path)

        file.installing(other) { Installation.Refused("no") }
        assertFailsWith<IOException> { file.installing(other) { throw IOException("disk") } }

        assertEquals(before, Files.readString(file.path))
        assertEquals(listOf("launch.args"), namesIn(home))
    }

    @Test
    fun `a reinstall that succeeds replaces the file`() = inTemporaryDirectory { home ->
        val file = LaunchFile(home.resolve("launch.args"))
        file.installing(launch) { Installation.Installed }

        file.installing(other) { Installation.Installed }

        assertEquals(listOf("other"), file.classPath())
        assertEquals(listOf("launch.args"), namesIn(home))
    }

    @Test
    fun `a path that the character set of the system cannot hold is an error that names the file`() =
        inTemporaryDirectory { home ->
            val file = LaunchFile(home.resolve("launch.args"), Charsets.US_ASCII)
            val unmappable = ServiceLaunch(Path.of("java"), listOf("-cp", "j\u00e9"))

            val failure = assertFailsWith<IOException> { file.installing(unmappable) { Installation.Installed } }

            assertTrue(failure.message.orEmpty().contains(file.path.toString()), failure.message)
            assertEquals(emptyList(), namesIn(home))
        }

    @Test
    fun `the class path is the value after any of the three class path flags, and empty without a file`() =
        inTemporaryDirectory { home ->
            val file = LaunchFile(home.resolve("launch.args"))
            assertEquals(emptyList(), file.classPath())

            for (flag in listOf("-cp", "-classpath", "--class-path")) {
                file.installing(ServiceLaunch(Path.of("java"), listOf("-Dx=1", flag, "one", "Main"))) {
                    Installation.Installed
                }

                assertEquals(listOf("one"), file.classPath(), flag)
            }
        }
}

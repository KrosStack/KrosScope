package io.github.krosstack.kscope.cli.service.home

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.testing.test
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceDirectoryTest {
    private class Probe(private val property: (String) -> String?) : CliktCommand(name = "probe") {
        var found: ServiceDirectory? = null

        override fun run() {
            found = ServiceDirectory.of(this, property)
        }
    }

    private fun directoryFor(variables: Map<String, String>, property: (String) -> String?): Path {
        val probe = Probe(property)
        probe.test(emptyList(), envvars = variables) { probe.parse(it) }
        return checkNotNull(probe.found).root
    }

    private fun absolute(text: String): Path = Path.of(text).toAbsolutePath().normalize()

    @Test
    fun `the system property comes first, then the environment variable`() {
        val variables = mapOf(ServiceDirectory.VARIABLE to "from-variable")
        val properties = mapOf(ServiceDirectory.PROPERTY to "from-property")

        assertEquals(absolute("from-property"), directoryFor(variables, properties::get))
        assertEquals(absolute("from-variable"), directoryFor(variables) { null })
    }

    @Test
    fun `a blank property or variable is not a directory`() {
        val variables = mapOf(ServiceDirectory.VARIABLE to "from-variable")

        assertEquals(absolute("from-variable"), directoryFor(variables) { "  " })
        assertEquals(
            Path.of(System.getProperty("user.home"), ".kscope", "service").toAbsolutePath().normalize(),
            directoryFor(mapOf(ServiceDirectory.VARIABLE to " ")) { "" },
        )
    }

    @Test
    fun `the directory holds the files of the service by their names`() {
        val directory = ServiceDirectory(absolute("service"))

        assertEquals(
            listOf("service.toml", "service.json", "service.lock", "service.log", "launch.args", "run-entry.reg"),
            listOf(
                directory.file.path,
                directory.metadataFile,
                directory.lockFile,
                directory.logFile,
                directory.launchFile,
                directory.registrationFile,
            ).map { it.fileName.toString() },
        )
    }
}

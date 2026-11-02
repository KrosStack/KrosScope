package io.github.krosstack.kscope.analyzer.kotlin.process

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Where the worker classpath comes from. */
class WorkerLocatorTest {
    private val home: Path = Files.createTempDirectory("kscope-home")
    private val libraries: Path = Files.createDirectories(home.resolve("workers/kotlin/lib"))
    private val wildcard = "$libraries${File.separator}*"

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    private fun locator(
        properties: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
        jar: Path? = null,
    ) = WorkerLocator(property = properties::get, environment = environment::get, applicationJar = { jar })

    private fun WorkerLocator.classpath(): String {
        val arguments = assertIs<WorkerCommand.Runnable>(command()).arguments
        return arguments[arguments.indexOf("-cp") + 1]
    }

    @Test
    fun `the classpath property names the worker classpath`() {
        val classpath = "a.jar${File.pathSeparator}b.jar"
        assertEquals(classpath, locator(mapOf(WorkerLocator.CLASSPATH_PROPERTY to classpath)).classpath())
    }

    @Test
    fun `the classpath property wins over the kscope home`() {
        val properties = mapOf(
            WorkerLocator.CLASSPATH_PROPERTY to "explicit.jar",
            WorkerLocator.HOME_PROPERTY to "$home",
        )
        assertEquals("explicit.jar", locator(properties).classpath())
    }

    @Test
    fun `the libraries below the kscope home property are used`() {
        assertEquals(wildcard, locator(mapOf(WorkerLocator.HOME_PROPERTY to "$home")).classpath())
    }

    @Test
    fun `the kscope home variable is used when the property is absent`() {
        assertEquals(wildcard, locator(environment = mapOf(WorkerLocator.HOME_VARIABLE to "$home")).classpath())
    }

    @Test
    fun `the home is derived from the library directory of the running jar`() {
        val jar = Files.createFile(Files.createDirectories(home.resolve("lib")).resolve("app.jar"))
        assertEquals(wildcard, locator(jar = jar).classpath())
    }

    @Test
    fun `a jar outside a library directory does not tell the home`() {
        val jar = Files.createFile(home.resolve("app.jar"))
        assertIs<WorkerCommand.Missing>(locator(jar = jar).command())
    }

    @Test
    fun `a missing worker is reported with the way to install it`() {
        val missing = assertIs<WorkerCommand.Missing>(locator().command())
        assertContains(missing.reason, WorkerLocator.CLASSPATH_PROPERTY)
        assertContains(missing.reason, WorkerLocator.HOME_VARIABLE)
    }

    @Test
    fun `a home without worker libraries is missing`() {
        libraries.toFile().deleteRecursively()
        assertIs<WorkerCommand.Missing>(locator(mapOf(WorkerLocator.HOME_PROPERTY to "$home")).command())
    }
}

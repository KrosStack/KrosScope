package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** The command that starts the worker JVM. */
class WorkerLaunchTest {
    private fun arguments(
        environment: Map<String, String> = emptyMap(),
        settings: WorkerSettings = WorkerSettings.NONE,
    ): List<String> {
        val properties = mapOf("java.home" to "/jdk", WorkerLocator.CLASSPATH_PROPERTY to "worker.jar")
        val locator = WorkerLocator(properties::get, environment::get) { null }
        return assertIs<WorkerCommand.Runnable>(locator.command(settings)).arguments
    }

    @Test
    fun `the java executable is the one of the running jvm`() {
        assertEquals(Path.of("/jdk", "bin", "java").toString(), arguments().first())
    }

    @Test
    fun `the worker main class follows the classpath`() {
        val arguments = arguments()
        assertEquals(listOf("-cp", "worker.jar", WorkerLocator.WORKER_MAIN_CLASS), arguments.takeLast(3))
    }

    @Test
    fun `the heap defaults to two gigabytes`() {
        assertContains(arguments(), "-Xmx2g")
    }

    @Test
    fun `the heap can be set with an environment variable`() {
        val arguments = arguments(mapOf(WorkerLocator.HEAP_VARIABLE to "6g"))
        assertContains(arguments, "-Xmx6g")
        assertFalse("-Xmx2g" in arguments)
    }

    @Test
    fun `a blank heap setting is ignored`() {
        assertContains(arguments(mapOf(WorkerLocator.HEAP_VARIABLE to " ")), "-Xmx2g")
    }

    @Test
    fun `the heap of the settings applies when the variable is not set`() {
        val arguments = arguments(settings = WorkerSettings(heapMebibytes = 512))
        assertContains(arguments, "-Xmx512m")
        assertFalse("-Xmx2g" in arguments)
    }

    @Test
    fun `the heap variable beats the heap of the settings`() {
        val arguments = arguments(mapOf(WorkerLocator.HEAP_VARIABLE to "6g"), WorkerSettings(heapMebibytes = 512))
        assertContains(arguments, "-Xmx6g")
        assertFalse("-Xmx512m" in arguments)
    }

    @Test
    fun `a heap variable that is not a size falls through to the settings`() {
        for (value in listOf(" ", "a lot", "12x", "0", "-1g", "1.5g")) {
            val arguments = arguments(mapOf(WorkerLocator.HEAP_VARIABLE to value), WorkerSettings(heapMebibytes = 512))
            assertContains(arguments, "-Xmx512m", value)
        }
        assertContains(arguments(mapOf(WorkerLocator.HEAP_VARIABLE to "a lot")), "-Xmx2g")
    }

    @Test
    fun `virtual machine output is kept off the protocol stream`() {
        assertContains(arguments(), "-XX:+DisplayVMOutputToStderr")
    }
}

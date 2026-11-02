package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchPlanTest {
    private val directory = ServiceDirectory(Path.of("service dir").toAbsolutePath())
    private val tail = listOf("-cp", "lib/a.jar", LaunchPlan.MAIN_CLASS, "service", "run", "--log")
    private val home = "-Dkscope.service.home=${directory.root}"

    private fun launch(options: List<String>, limits: ServiceLimits = ServiceLimits()): ServiceLaunch =
        LaunchPlan(JvmProcess(Path.of("jdk/bin/java"), "lib/a.jar", options)).launch(directory, limits)

    @Test
    fun `the launch runs the same java with the class path, the service directory and the run command`() {
        val launch = launch(emptyList())

        assertEquals(Path.of("jdk/bin/java"), launch.executable)
        assertEquals(listOf(home) + tail, launch.arguments)
    }

    @Test
    fun `system properties and -X options of this process are passed on, and other options are not`() {
        val options = listOf("-Dfile.encoding=UTF-8", "-Xss2m", "-XX:+UseSerialGC", "--add-opens=a/b=c", "-Dother=1")

        val launch = launch(options)

        assertEquals(listOf("-Dfile.encoding=UTF-8", "-Xss2m", "-Dother=1", home) + tail, launch.arguments)
    }

    @Test
    fun `the service directory of this process is replaced and never repeated`() {
        val launch = launch(listOf("-Dkscope.service.home=/somewhere/else"))

        assertEquals(listOf(home) + tail, launch.arguments)
    }

    @Test
    fun `memory-mib replaces any maximum heap`() {
        val launch = launch(listOf("-Xmx512m", "-Xss2m"), ServiceLimits(memoryMib = 2048))

        assertEquals(listOf("-Xss2m", "-Xmx2048m", home) + tail, launch.arguments)
    }

    @Test
    fun `memory-mib also drops the inherited initial and young heap sizes, which could exceed the new maximum`() {
        val options = listOf("-Xms8g", "-Xmx16g", "-Xmn4g", "-Xss2m", "-Xshare:auto")

        val launch = launch(options, ServiceLimits(memoryMib = 512))

        assertEquals(listOf("-Xss2m", "-Xshare:auto", "-Xmx512m", home) + tail, launch.arguments)
    }

    @Test
    fun `without memory-mib the heap options of this process are kept, and so they are for 0`() {
        val options = listOf("-Xms256m", "-Xmx512m")
        val expected = options + home + tail

        assertEquals(expected, launch(options).arguments)
        assertEquals(expected, launch(options, ServiceLimits(memoryMib = 0)).arguments)
    }

    @Test
    fun `the main class of the launch is the main class of kscope`() {
        val main = Class.forName(LaunchPlan.MAIN_CLASS)

        assertEquals("main", main.getMethod("main", Array<String>::class.java).name)
    }
}

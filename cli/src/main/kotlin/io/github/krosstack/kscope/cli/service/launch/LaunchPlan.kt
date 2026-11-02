package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceLimits

internal class LaunchPlan(private val jvm: JvmProcess, private val mainClass: String = MAIN_CLASS) {
    fun launch(directory: ServiceDirectory, limits: ServiceLimits): ServiceLaunch {
        val heap = limits.memoryMib?.takeIf { it > 0 }?.let { "$MAXIMUM_HEAP${it}m" }
        val inherited = jvm.options.filter(::isInherited).filterNot { heap != null && isHeapSizing(it) }
        val arguments = inherited + listOfNotNull(heap) + "$HOME_OPTION${directory.root}" +
            listOf("-cp", jvm.classPath, mainClass, "service", "run", "--log")
        return ServiceLaunch(jvm.executable, arguments)
    }

    private fun isInherited(option: String): Boolean = !option.startsWith(HOME_OPTION) && isPropertyOrExtension(option)

    private fun isPropertyOrExtension(option: String): Boolean =
        option.startsWith("-D") || (option.startsWith("-X") && !option.startsWith("-XX"))

    // A new maximum heap is refused by the JVM when an inherited initial or young size is above it.
    private fun isHeapSizing(option: String): Boolean = HEAP_SIZING.any { option.startsWith(it) }

    companion object {
        const val MAIN_CLASS = "io.github.krosstack.kscope.cli.MainKt"
        private const val MAXIMUM_HEAP = "-Xmx"
        private const val HOME_OPTION = "-D${ServiceDirectory.PROPERTY}="
        private val HEAP_SIZING = listOf(MAXIMUM_HEAP, "-Xms", "-Xmn")
    }
}

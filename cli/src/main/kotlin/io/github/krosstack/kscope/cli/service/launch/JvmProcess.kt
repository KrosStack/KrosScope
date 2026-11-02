package io.github.krosstack.kscope.cli.service.launch

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path

internal data class JvmProcess(val executable: Path, val classPath: String, val options: List<String>) {
    companion object {
        fun current(): JvmProcess = of(
            command = ProcessHandle.current().info().command().orElse(null),
            javaHome = System.getProperty("java.home"),
            osName = System.getProperty("os.name"),
            classPath = System.getProperty("java.class.path"),
            options = ManagementFactory.getRuntimeMXBean().inputArguments,
        )

        fun of(command: String?, javaHome: String, osName: String, classPath: String, options: List<String>) =
            JvmProcess(withoutConsole(java(command, javaHome, osName), osName), classPath, options)

        private fun java(command: String?, javaHome: String, osName: String): Path {
            val name = if (isWindows(osName)) "java.exe" else "java"
            return command?.let { Path.of(it) } ?: Path.of(javaHome, "bin", name)
        }

        private fun withoutConsole(java: Path, osName: String): Path =
            if (isWindows(osName)) java.resolveSibling("javaw.exe").takeIf(Files::exists) ?: java else java

        private fun isWindows(osName: String) = osName.startsWith("Windows", ignoreCase = true)
    }
}

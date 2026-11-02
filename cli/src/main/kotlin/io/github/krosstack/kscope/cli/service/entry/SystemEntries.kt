package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class SystemEntries(
    private val osName: String,
    private val home: Path,
    private val environment: (String) -> String?,
    private val runner: CommandRunner,
) : EntryFactory {
    override fun entryFor(directory: ServiceDirectory): SessionEntry {
        val name = osName.lowercase()
        return when {
            name.startsWith("windows") -> WindowsRunEntry(directory, runner, environment)
            name.startsWith("linux") -> SystemdUserEntry(directory, configHome(), runner)
            name.startsWith("mac") -> LaunchAgentEntry(directory, home)
            else -> UnsupportedEntry(osName)
        }
    }

    private fun configHome(): Path = environment("XDG_CONFIG_HOME")?.let(::absolutePath) ?: home.resolve(".config")

    private fun absolutePath(text: String): Path? = try {
        Path.of(text).takeIf { text.isNotBlank() && it.isAbsolute }
    } catch (_: InvalidPathException) {
        null
    }

    companion object {
        fun ofThisMachine(): SystemEntries = SystemEntries(
            osName = System.getProperty("os.name"),
            home = Path.of(System.getProperty("user.home")),
            environment = System::getenv,
            runner = ProcessCommandRunner(),
        )
    }
}

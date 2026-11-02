package io.github.krosstack.kscope.benchmark.agent.launch

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal object ExecutableLocator {
    fun find(name: String, environment: Map<String, String> = System.getenv(), windows: Boolean = isWindows()): Path? {
        val search = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value.orEmpty()
        val extensions = if (windows) windowsExtensions(name, environment) else listOf("")
        return search.split(File.pathSeparator).filter { it.isNotBlank() }.firstNotNullOfOrNull { directory ->
            extensions.map { Path.of(directory, name + it) }.firstOrNull { Files.isRegularFile(it) }
        }
    }

    private fun windowsExtensions(name: String, environment: Map<String, String>): List<String> {
        val configured = environment.entries.firstOrNull { it.key.equals("PATHEXT", ignoreCase = true) }?.value
        val listed = (configured ?: DEFAULT_EXTENSIONS).split(';').filter { it.isNotBlank() }
        return if ('.' in name) listOf("") + listed else listed
    }

    private const val DEFAULT_EXTENSIONS = ".COM;.EXE;.BAT;.CMD"
}

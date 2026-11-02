package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.launch.LaunchArguments
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class LaunchFile(val path: Path, private val charset: Charset = nativeCharset()) {
    private val staged: Path = path.resolveSibling(path.fileName.toString() + ".tmp")

    fun exists(): Boolean = Files.exists(path)

    fun commandWords(launch: ServiceLaunch): List<String> = listOf(launch.executable.toString(), "@$path")

    fun delete() {
        Files.deleteIfExists(path)
        Files.deleteIfExists(staged)
    }

    fun classPath(): List<String> = try {
        classPathOf(LaunchArguments.parse(Files.readString(path, charset)))
    } catch (_: IOException) {
        emptyList()
    }

    fun installing(launch: ServiceLaunch, register: () -> Installation): Installation {
        try {
            stage(launch)
            val installation = register()
            if (installation is Installation.Installed) {
                Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            return installation
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    private fun stage(launch: ServiceLaunch) {
        Files.createDirectories(path.parent)
        try {
            Files.writeString(staged, LaunchArguments.render(launch.arguments), charset)
        } catch (_: CharacterCodingException) {
            throw IOException("$path cannot hold a path with characters outside ${charset.name()}.")
        }
    }

    private fun classPathOf(arguments: List<String>): List<String> {
        val flag = arguments.indexOfFirst { it in CLASS_PATH_FLAGS }
        val value = if (flag < 0) null else arguments.getOrNull(flag + 1)
        return value?.split(File.pathSeparator).orEmpty().filter { it.isNotEmpty() }
    }

    private companion object {
        val CLASS_PATH_FLAGS = setOf("-cp", "-classpath", "--class-path")

        fun nativeCharset(): Charset = try {
            Charset.forName(System.getProperty("sun.jnu.encoding"))
        } catch (_: IllegalArgumentException) {
            Charset.defaultCharset()
        }
    }
}

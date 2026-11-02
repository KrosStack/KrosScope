package io.github.krosstack.kscope.cli.service.home

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class ServiceFile(val path: Path) {
    fun read(): ServiceFileReading = try {
        parse(Files.readString(path, Charsets.UTF_8))
    } catch (_: NoSuchFileException) {
        ServiceFileReading.Read(emptyList())
    } catch (_: CharacterCodingException) {
        ServiceFileReading.Unreadable("$path: the file is not valid UTF-8")
    } catch (failure: IOException) {
        ServiceFileReading.Unreadable("$path: ${failure.message}")
    }

    fun <T> exclusively(block: () -> T): T =
        ServiceFileLocks.exclusively(path.resolveSibling(path.fileName.toString() + ".lock"), block)

    fun write(workspaces: Collection<String>, limits: ServiceLimits = ServiceLimits()) {
        val directory = path.parent
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "service-", ".tmp")
        try {
            val text = ServiceFileText.render(workspaces.distinct().sorted(), limits)
            Files.writeString(temporary, text, Charsets.UTF_8)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun parse(text: String): ServiceFileReading {
        val toml = Toml.parse(text)
        val problem = syntaxProblem(toml) ?: unknownKeyProblem(toml) ?: valueProblem(toml)
            ?: ServiceFileLimits.problem(toml)
        return if (problem == null) {
            val workspaces = toml.getArrayOrEmpty(KEY).toList().filterIsInstance<String>().distinct()
            ServiceFileReading.Read(workspaces, ServiceFileLimits.of(toml))
        } else {
            ServiceFileReading.Unreadable("$path: $problem")
        }
    }

    private fun syntaxProblem(toml: TomlParseResult): String? =
        toml.errors().firstOrNull()?.let { "${it.message} (${it.position()})" }

    private fun unknownKeyProblem(toml: TomlParseResult): String? = toml.keySet().firstOrNull { it !in KEYS }?.let {
        "unknown key \"$it\" (${toml.inputPositionOf(listOf(it))}); the keys are $KEY_LIST"
    }

    private fun valueProblem(toml: TomlParseResult): String? = when (val value = toml.get(listOf(KEY))) {
        null -> null
        is TomlArray -> (0 until value.size()).firstNotNullOfOrNull { elementProblem(value, it) }
        else -> "\"$KEY\" must be an array of strings (${toml.inputPositionOf(listOf(KEY))})"
    }

    private fun elementProblem(array: TomlArray, index: Int): String? {
        val element = array.get(index)
        val where = "\"$KEY\" element ${index + 1}"
        return when {
            element !is String -> "$where must be a string (${array.inputPositionOf(index)})"
            !isAbsolute(element) -> "$where is not an absolute path (${array.inputPositionOf(index)})"
            else -> null
        }
    }

    private fun isAbsolute(value: String): Boolean = try {
        Path.of(value).isAbsolute
    } catch (_: InvalidPathException) {
        false
    }

    companion object {
        const val KEY = "workspaces"
        private val KEYS = setOf(KEY) + ServiceFileLimits.KEYS
        private const val KEY_LIST =
            "\"$KEY\", \"${ServiceFileLimits.IDLE_KEY}\" and \"${ServiceFileLimits.MEMORY_KEY}\""
    }
}

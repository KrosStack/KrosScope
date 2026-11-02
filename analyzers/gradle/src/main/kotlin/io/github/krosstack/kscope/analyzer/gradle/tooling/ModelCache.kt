package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The fetched model documents, kept below the index directory under the fingerprint of the build inputs. */
internal class ModelCache(gradleModelDirectory: Path) {
    private val directory = gradleModelDirectory.resolve("models")

    fun read(key: String): String? = fileFor(key).takeIf(Files::isRegularFile)?.let(Files::readString)

    /** Only the newest model is kept: an older fingerprint can only come back by restoring older inputs. */
    fun write(key: String, json: String) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "model", ".tmp")
        Files.writeString(temporary, json)
        Files.move(temporary, fileFor(key), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Files.list(directory).use { files ->
            files.filter { it != fileFor(key) }.forEach(Files::deleteIfExists)
        }
    }

    private fun fileFor(key: String): Path = directory.resolve("$key.json")
}

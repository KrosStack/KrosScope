package io.github.krosstack.kscope.benchmark.agent.workspace

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object FileTrees {
    fun copy(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun deleteQuietly(path: Path): Boolean {
        if (!Files.exists(path)) return true
        return try {
            Files.walk(path).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { entry ->
                    entry.toFile().setWritable(true)
                    Files.deleteIfExists(entry)
                }
            }
            true
        } catch (_: IOException) {
            false
        }
    }
}

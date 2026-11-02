package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.RepositoryId
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The archives that the build of a module leaves in its own directory, in the order they are read: the JAR of the
 * given name in `build/libs`, then the AARs in `build/outputs/aar` whose names start with the name of the module, its
 * directory or the archive, in name order.
 */
internal class ProducerOutputs(private val roots: CheckoutRoots) {
    fun of(repository: RepositoryId?, producer: Module, fileName: String): List<Path> {
        val directory = producer.directory.value
        val jar = roots.resolve(repository, joined(directory, BUILT_JARS, fileName))
        val built = roots.resolve(repository, joined(directory, BUILT_AARS))
        val aars = built?.let { aarsIn(it, namesOf(producer, fileName)) }.orEmpty()
        return listOfNotNull(jar) + aars
    }

    private fun joined(vararg parts: String): String = parts.filter(String::isNotEmpty).joinToString("/")

    private fun namesOf(producer: Module, fileName: String): Set<String> = setOf(
        producer.path.substringAfterLast(':'),
        producer.directory.value.substringAfterLast('/'),
        fileName.substringBeforeLast('.'),
    ).filter(String::isNotEmpty).toSet()

    private fun aarsIn(directory: Path, names: Set<String>): List<Path> = try {
        Files.list(directory).use { entries ->
            entries.filter { Files.isRegularFile(it) && isAarNamedAfter(it, names) }.sorted().toList()
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: UncheckedIOException) {
        emptyList()
    }

    private fun isAarNamedAfter(file: Path, names: Set<String>): Boolean {
        val name = file.fileName.toString()
        return name.endsWith(AAR) && names.any(name::startsWith)
    }

    private companion object {
        const val BUILT_JARS = "build/libs"
        const val BUILT_AARS = "build/outputs/aar"
        const val AAR = ".aar"
    }
}

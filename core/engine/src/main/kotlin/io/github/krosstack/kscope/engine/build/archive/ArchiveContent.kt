package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.parentOf
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Path

/**
 * Compares a JAR or AAR on disk with the module that is meant to have built it. The archive is looked for where the
 * consumer declares it, or where a registered folder holds it, then in the build outputs of the module
 * ([ProducerOutputs]). It matches when it has classes, all of them in packages of the module's sources, and at least
 * one class that a source file of the module names.
 */
internal class ArchiveContent(
    private val roots: CheckoutRoots,
    private val stems: ProducerStems,
    private val repositoryOf: (Module) -> RepositoryId?,
) {
    private val outputs = ProducerOutputs(roots)

    fun stateOf(archive: DeclaredArchive, producer: Module): ContentState {
        val classes = locations(archive, producer).firstNotNullOfOrNull(JarClasses::stemsIn)
        return when {
            classes == null -> ContentState.ABSENT
            matches(classes, stems.of(producer.id)) -> ContentState.MATCHED
            else -> ContentState.DIFFERS
        }
    }

    private fun locations(archive: DeclaredArchive, producer: Module): List<Path> =
        listOfNotNull(archive.found ?: roots.resolve(archive.repository, archive.path)) +
            outputs.of(repositoryOf(producer), producer, archive.fileName)

    private fun matches(classes: Set<String>, sources: Set<String>): Boolean {
        val packages = sources.map(::parentOf).toSet()
        return classes.all { parentOf(it) in packages } && classes.any { it in sources }
    }
}

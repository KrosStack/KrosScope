package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Path

/**
 * A JAR or AAR file that [consumer] depends on: the dependency [edge] that names it, the repository it is declared in
 * and its [path] there, which may leave the repository. For a dependency on a coordinate, [path] is the name of the
 * file and [found] is where a registered folder holds it.
 */
internal class DeclaredArchive(
    val edge: Relationship,
    val consumer: Module,
    val repository: RepositoryId?,
    val path: String,
    val found: Path? = null,
) {
    val fileName: String get() = path.substringAfterLast('/')
}

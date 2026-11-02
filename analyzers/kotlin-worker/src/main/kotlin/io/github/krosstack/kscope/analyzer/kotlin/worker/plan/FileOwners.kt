package io.github.krosstack.kscope.analyzer.kotlin.worker.plan

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSetId
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The module and source set a file belongs to. [artifact] is null for synthetic sources, which stand in for generated
 * code and have no repository file of their own.
 */
internal class FileOwner(
    val moduleName: String,
    val module: ModuleId,
    val sourceSet: SourceSetId,
    val artifact: ArtifactRef?,
) {
    val sourceSetName: String = sourceSet.value.removePrefix("${module.value}@")
}

/**
 * Maps absolute file paths, as the analysis session reports them, to the file's owner. A directory shared by two
 * analysis modules has one owner per module; a lookup without a module name answers with the first.
 */
internal class FileOwners(claims: List<Pair<Path, FileOwner>>) {
    private val byModuleAndPath: Map<Pair<String, Path>, FileOwner> =
        claims.map { (path, owner) -> (owner.moduleName to normalized(path)) to owner }.reversed().toMap()
    private val firstByPath: Map<Path, FileOwner> =
        claims.map { (path, owner) -> normalized(path) to owner }.reversed().toMap()

    fun of(path: String, moduleName: String? = null): FileOwner? {
        val key = try {
            normalized(Path.of(path))
        } catch (_: InvalidPathException) {
            return null
        }
        return if (moduleName == null) firstByPath[key] else byModuleAndPath[moduleName to key]
    }

    companion object {
        fun normalized(path: Path): Path = path.toAbsolutePath().normalize()
    }
}

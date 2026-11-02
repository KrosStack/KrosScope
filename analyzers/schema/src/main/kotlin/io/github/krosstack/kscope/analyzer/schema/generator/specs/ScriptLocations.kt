package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.analyzer.schema.generator.script.PathAnchor
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptPath
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.BuildModel
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The places on disk that the paths of a build script name. A path is made absolute from the directory of the build
 * root of the script when `$rootDir` starts it, and from the directory of the module of the script otherwise, in the
 * checkout of the repository of the script, and is normalized. The places are used to compare paths on disk and never
 * leave this analysis. A place can also be found in the checkout that holds it, which may be that of another
 * repository.
 */
internal class ScriptLocations(private val model: BuildModel, private val roots: CheckoutRoots) {
    fun of(path: ScriptPath, repository: RepositoryId, module: Module): Path? {
        val root = roots.of(repository)
        val buildRoot = model.buildRoots.firstOrNull { it.id == module.buildRoot }
        val base = when (path.anchor) {
            PathAnchor.BUILD_ROOT -> buildRoot?.let { root?.resolve(it.directory.value) }
            PathAnchor.MODULE, PathAnchor.PLAIN -> root?.resolve(module.directory.value)
        }
        return base?.let { resolved(it, path.relative) }
    }

    fun inCheckout(path: ScriptPath, repository: RepositoryId, module: Module): CheckoutPath? =
        of(path, repository, module)?.let(roots::locate)

    private fun resolved(base: Path, relative: String): Path? = try {
        base.resolve(relative).normalize()
    } catch (_: InvalidPathException) {
        null
    }
}

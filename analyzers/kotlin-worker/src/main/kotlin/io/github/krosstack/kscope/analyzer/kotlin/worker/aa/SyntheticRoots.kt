package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class SyntheticFile(val path: Path, val owner: WorkerSourceSet, val module: WorkerModule)

/** Synthetic sources written to a temporary directory so the session can treat them as an extra source root. */
internal class SyntheticRoots private constructor(
    private val base: Path?,
    private val roots: Map<WorkerSourceSet, Path>,
    val files: List<SyntheticFile>,
) : AutoCloseable {
    fun rootOf(sourceSet: WorkerSourceSet): Path? = roots[sourceSet]

    override fun close() {
        base?.toFile()?.deleteRecursively()
    }

    companion object {
        fun write(modules: List<WorkerModule>): SyntheticRoots {
            val withSources = modules.flatMap { module -> module.sourceSets.map { module to it } }
                .filter { it.second.syntheticSources.isNotEmpty() }
            if (withSources.isEmpty()) return SyntheticRoots(null, emptyMap(), emptyList())
            val base = Files.createTempDirectory("kscope-synthetic")
            val roots = mutableMapOf<WorkerSourceSet, Path>()
            val files = mutableListOf<SyntheticFile>()
            withSources.forEachIndexed { index, (module, sourceSet) ->
                val root = Files.createDirectory(base.resolve(index.toString()))
                roots[sourceSet] = root
                files += writeSources(root, module, sourceSet)
            }
            return SyntheticRoots(base, roots, files)
        }

        private fun writeSources(root: Path, module: WorkerModule, sourceSet: WorkerSourceSet): List<SyntheticFile> =
            sourceSet.syntheticSources.mapNotNull { source ->
                val target = targetOf(root, source)
                target?.let {
                    Files.createDirectories(it.parent)
                    Files.writeString(it, source.text)
                    SyntheticFile(it, sourceSet, module)
                }
            }

        // A virtual path without a source extension gets the one of the stub's language, so the session reads it.
        private fun targetOf(root: Path, source: SyntheticSource): Path? {
            val extension = if (source.language == "java") ".java" else ".kt"
            val named = if (source.virtualPath.endsWith(".kt") || source.virtualPath.endsWith(".java")) {
                source.virtualPath
            } else {
                source.virtualPath + extension
            }
            val target = try {
                root.resolve(named).normalize()
            } catch (_: InvalidPathException) {
                return null
            }
            return target.takeIf { it.startsWith(root) && it != root }
        }
    }
}

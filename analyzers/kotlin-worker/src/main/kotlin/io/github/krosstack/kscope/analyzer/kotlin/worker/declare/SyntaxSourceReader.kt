package io.github.krosstack.kscope.analyzer.kotlin.worker.declare

import com.intellij.psi.PsiFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.OwnedFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.SourceSetFiles
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwner
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Parses the files of the source sets that ask for facts, without a session. A file that two source sets of a module
 * both list belongs to the first, as in a session; a file that is missing or is neither Kotlin nor Java is reported
 * as missing.
 */
internal class SyntaxSourceReader(private val parser: ScriptParser) {
    fun sourceSetsOf(modules: List<WorkerModule>): Sequence<SourceSetFiles> = modules.asSequence().flatMap { module ->
        val claimed = mutableSetOf<Path>()
        module.sourceSets.asSequence().mapNotNull { sourceSet -> readSourceSet(module, sourceSet, claimed) }
    }

    private fun readSourceSet(
        module: WorkerModule,
        sourceSet: WorkerSourceSet,
        claimed: MutableSet<Path>,
    ): SourceSetFiles? {
        val (present, missing) = sourceSet.files.partition { pathOf(it) != null }
        val owned = present.filter { claimed.add(checkNotNull(pathOf(it))) }
        if (!sourceSet.emitFactsFor) return null
        val parsed = owned.map { file -> file to parse(file) }
        val unreadable = missing + parsed.filter { it.second == null }.map { it.first }
        val files = parsed.mapNotNull { (file, psi) ->
            psi?.let { OwnedFile(it, ownerOf(module, sourceSet, file), file) }
        }
        return SourceSetFiles(module.name, sourceSet, files, unreadable, module.classpath.isNotEmpty())
    }

    private fun parse(file: WorkerFile): PsiFile? {
        val text = pathOf(file)?.let(::textOf) ?: return null
        val name = Path.of(file.absolutePath).fileName.toString()
        return if (name.lowercase().endsWith(".java")) parser.parseJava(name, text) else parser.parse(name, text)
    }

    private fun textOf(path: Path): String? = try {
        String(Files.readAllBytes(path), Charsets.UTF_8)
    } catch (_: IOException) {
        null
    }

    private fun pathOf(file: WorkerFile): Path? {
        val path = try {
            Path.of(file.absolutePath)
        } catch (_: InvalidPathException) {
            return null
        }
        val name = path.fileName?.toString().orEmpty().lowercase()
        val isSource = name.endsWith(".kt") || name.endsWith(".java")
        return path.takeIf { isSource && Files.isRegularFile(it) }?.toAbsolutePath()?.normalize()
    }

    private fun ownerOf(module: WorkerModule, sourceSet: WorkerSourceSet, file: WorkerFile) =
        FileOwner(module.name, sourceSet.module, sourceSet.sourceSet, file.ref)
}

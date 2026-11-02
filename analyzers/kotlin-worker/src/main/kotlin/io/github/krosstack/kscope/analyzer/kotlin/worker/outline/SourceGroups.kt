package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.ScriptOutline
import io.github.krosstack.kscope.spi.worker.WorkerFile
import org.jetbrains.kotlin.psi.KtFile

private val sourceSetDirectory = Regex("""^(.*?)/?src/[^/]+/(?:kotlin|java)/.*$""")

/**
 * Splits the Kotlin sources of a request into source sets by their project directory, the path above `src/<set>`,
 * so that a plugin class sees the functions of its own project only.
 */
internal class SourceGroups(sources: List<Pair<WorkerFile, KtFile>>) {
    private val groupOf: Map<WorkerFile, String> = sources.associate { (file, _) -> file to projectDirectory(file) }

    private val outliners: Map<String, SourceOutliner> =
        sources.groupBy({ groupOf.getValue(it.first) }, { it.second }).mapValues { (_, files) -> SourceOutliner(files) }

    fun outline(file: WorkerFile, parsed: KtFile): ScriptOutline =
        outliners.getValue(groupOf.getValue(file)).outline(parsed)

    private fun projectDirectory(file: WorkerFile): String {
        val path = file.ref.path.value
        return sourceSetDirectory.matchEntire(path)?.groupValues?.get(1) ?: path.substringBeforeLast('/', "")
    }
}

package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.KotlinWorkerProcess
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerStart
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.ScriptOutliner
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import io.github.krosstack.kscope.spi.worker.WorkerFile

/**
 * Outlines Kotlin scripts, such as `build.gradle.kts`, and the Kotlin sources of build logic, whose outline lists their
 * plugin classes, with the analyzer worker in a child JVM. Every artifact it is given is sent to the worker, so callers
 * pass Kotlin files only, and sources that are to inline each other's functions in one call. When the worker is
 * unavailable or fails, the files it did not answer for are absent from the result.
 */
public class KotlinScriptOutliner internal constructor(
    private val launcher: WorkerLauncher,
    private val workerOutput: (String) -> Unit,
) : ScriptOutliner {
    public constructor(workerOutput: (String) -> Unit = {}) : this(WorkerLauncher.fromEnvironment(), workerOutput)

    public constructor(settings: () -> WorkerSettings, workerOutput: (String) -> Unit) :
        this(WorkerLauncher.fromEnvironment(settings), workerOutput)

    override fun outline(scripts: List<Artifact>, files: WorkspaceFiles): Map<ArtifactRef, ScriptOutline> {
        if (scripts.isEmpty()) return emptyMap()
        val workerFiles = scripts.map { it.toWorkerFile(files) }
        return when (val start = launcher.start(workerOutput)) {
            is WorkerStart.Unavailable -> emptyMap()
            is WorkerStart.Started -> start.process.use { outlineWith(it, workerFiles) }
        }
    }

    private fun outlineWith(process: KotlinWorkerProcess, scripts: List<WorkerFile>): Map<ArtifactRef, ScriptOutline> {
        val outlines = linkedMapOf<ArtifactRef, ScriptOutline>()
        process.outline(scripts) { ref, outline -> outlines[ref] = outline }
        return outlines
    }
}

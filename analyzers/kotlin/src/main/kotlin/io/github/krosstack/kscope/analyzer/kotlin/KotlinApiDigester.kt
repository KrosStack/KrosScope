package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.KotlinWorkerProcess
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerOutcome
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerStart
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ApiDigester
import io.github.krosstack.kscope.spi.ApiDigests
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.WorkerFile

/**
 * Digests Kotlin source APIs in the analyzer worker. Requests contain at most 500 sources and share one worker;
 * when it is unavailable or fails, the sources it did not answer for are in neither part of the result.
 */
public class KotlinApiDigester internal constructor(
    private val launcher: WorkerLauncher,
    private val workerOutput: (String) -> Unit,
) : ApiDigester {
    public constructor(workerOutput: (String) -> Unit = {}) : this(WorkerLauncher.fromEnvironment(), workerOutput)

    public constructor(settings: () -> WorkerSettings, workerOutput: (String) -> Unit) :
        this(WorkerLauncher.fromEnvironment(settings), workerOutput)

    override val version: String = "1"
    override val consumes: Set<ArtifactType> = setOf(KOTLIN_SOURCE)

    override fun digest(sources: List<Artifact>, files: WorkspaceFiles): ApiDigests {
        if (sources.isEmpty()) return ApiDigests.NONE
        val workerFiles = sources.map { it.toWorkerFile(files) }
        return when (val start = launcher.start(workerOutput)) {
            is WorkerStart.Unavailable -> ApiDigests.NONE
            is WorkerStart.Started -> start.process.use { digestWith(it, workerFiles) }
        }
    }

    private fun digestWith(process: KotlinWorkerProcess, sources: List<WorkerFile>): ApiDigests {
        val digests = linkedMapOf<ArtifactRef, String>()
        val withoutDigest = linkedSetOf<ArtifactRef>()
        for (chunk in sources.chunked(MAX_SOURCES_PER_REQUEST)) {
            val outcome = process.digestApi(chunk) { ref, digest ->
                if (digest == null) withoutDigest.add(ref) else digests[ref] = digest
            }
            if (outcome is WorkerOutcome.Failed) break
        }
        return ApiDigests(digests, withoutDigest)
    }

    internal companion object {
        val KOTLIN_SOURCE = ArtifactType("kotlin.source", ArtifactCategory.SOURCE)
        private const val MAX_SOURCES_PER_REQUEST = 500
    }
}

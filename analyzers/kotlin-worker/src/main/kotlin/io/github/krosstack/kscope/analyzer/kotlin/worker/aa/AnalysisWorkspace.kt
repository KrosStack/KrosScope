package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import com.intellij.psi.PsiFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwner
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwners
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.PlanProblem
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession

internal class OwnedFile(val psi: PsiFile, val owner: FileOwner, val requested: WorkerFile)

/**
 * The requested files of one source set that the session found, and those it could not find. [hasClasspath] tells
 * whether the request gave its module a classpath, which decides why an unresolved reference is unresolved.
 */
internal class SourceSetFiles(
    val moduleName: String,
    val source: WorkerSourceSet,
    val files: List<OwnedFile>,
    val missing: List<WorkerFile>,
    val hasClasspath: Boolean,
)

/**
 * An open analysis session together with the mapping from every file to the module and source set that owns it.
 * Closing it disposes the session and removes the synthetic sources written for it.
 */
internal class AnalysisWorkspace(
    val session: StandaloneAnalysisAPISession,
    val sourceSets: List<SourceSetFiles>,
    val owners: FileOwners,
    val planProblems: List<PlanProblem>,
    private val release: () -> Unit,
) : AutoCloseable {
    override fun close() = release()
}

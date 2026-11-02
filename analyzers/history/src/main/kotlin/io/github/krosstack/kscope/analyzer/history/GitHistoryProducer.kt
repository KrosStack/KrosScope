package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.InputBasis
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.RepositoryCheckout
import java.nio.file.Path

/**
 * Turns the Git history of the workspace into a number of metrics per artifact and `CO_CHANGES_WITH` relationships
 * between artifacts that changed together. It reads the last [CommitLog.MAX_COMMITS] commits that are not merges. When
 * Git, a work tree or a second commit is missing, it says so in one `history.not-available` diagnostic and emits
 * nothing else. A renamed file has the history it gained under its current path only.
 */
internal class GitHistoryProducer(private val git: GitProcess = GitProcess()) : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.history.git", "1"),
        consumes = emptySet(),
        capabilities = emptySet(),
        granularity = OwnershipKind.REPOSITORY,
        inputs = InputBasis.REPOSITORY_REVISION,
    )

    override fun produce(request: ProduceRequest, sink: FactSink) {
        val checkout = request.checkout
        request.units.forEach { unit ->
            val facts = checkout?.let { factsOf(unit, it) } ?: listOf(unavailable("the scan has no repository"))
            val revision = checkout?.repository?.headRevision.orEmpty()
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(revision), facts))
        }
    }

    private fun factsOf(unit: AnalysisUnit, checkout: RepositoryCheckout): List<Fact> {
        if (checkout.repository.headRevision == null) return listOf(unavailable("there is no commit"))
        return when (val log = readLog(checkout.root)) {
            is LogOutcome.Unavailable -> listOf(unavailable(log.reason))
            is LogOutcome.Read -> HistoryFacts(unit.artifacts).of(summaryOf(log, unit.artifacts))
        }
    }

    private fun summaryOf(log: LogOutcome.Read, artifacts: List<Artifact>): HistorySummary {
        val inventory = artifacts.mapTo(HashSet()) { it.ref.path.value }
        return HistoryAnalysis.summarize(log.commits.map { it.within(log.prefix) }, inventory)
    }

    private fun readLog(root: Path): LogOutcome = when (val located = locate(root)) {
        is Located.Failed -> LogOutcome.Unavailable(located.reason)
        is Located.At -> logAt(root, located.prefix)
    }

    private fun locate(root: Path): Located = when (val result = git.run(root, PREFIX_ARGUMENTS)) {
        is GitResult.Incomplete -> Located.Failed(result.reason)
        is GitResult.Completed -> locationFrom(result)
    }

    private fun locationFrom(result: GitResult.Completed): Located =
        if (result.exitCode == 0) Located.At(result.output.trim()) else Located.Failed(NOT_A_WORK_TREE)

    private fun logAt(root: Path, prefix: String): LogOutcome = when (val result = git.run(root, CommitLog.ARGUMENTS)) {
        is GitResult.Incomplete -> LogOutcome.Unavailable(result.reason)
        is GitResult.Completed -> commitsOf(result, prefix)
    }

    private fun commitsOf(result: GitResult.Completed, prefix: String): LogOutcome = when {
        result.exitCode != 0 -> LogOutcome.Unavailable("git log failed with exit code ${result.exitCode}")
        else -> readCommits(CommitLog.parse(result.output), prefix)
    }

    private fun readCommits(commits: List<GitCommit>, prefix: String): LogOutcome = when {
        commits.size < MIN_COMMITS -> LogOutcome.Unavailable("the history has fewer than $MIN_COMMITS commits")
        else -> LogOutcome.Read(commits, prefix)
    }

    private sealed interface Located {
        class At(val prefix: String) : Located

        class Failed(val reason: String) : Located
    }

    private sealed interface LogOutcome {
        class Unavailable(val reason: String) : LogOutcome

        class Read(val commits: List<GitCommit>, val prefix: String) : LogOutcome
    }

    private fun unavailable(reason: String) = Fact.OfDiagnostic(
        Diagnostic(
            severity = DiagnosticSeverity.INFO,
            code = ReasonCode("history.not-available"),
            message = "No history signals: $reason.",
            location = null,
        ),
    )

    private companion object {
        const val MIN_COMMITS = 2
        const val NOT_A_WORK_TREE = "the directory is not in a Git work tree"
        val PREFIX_ARGUMENTS = listOf("rev-parse", "--show-prefix")
    }
}

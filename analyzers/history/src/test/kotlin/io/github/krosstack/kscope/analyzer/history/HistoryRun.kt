package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.HistorySignals
import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.RepositoryCheckout
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path

private val repositoryId = RepositoryId("synthetic")
private val owner = OwnershipUnit(OwnershipKind.REPOSITORY, repositoryId.value)

private object NoFiles : WorkspaceFiles {
    override fun read(ref: ArtifactRef): ByteArray = error("The history producer reads no file")

    override fun absolutePath(ref: ArtifactRef): Path = error("The history producer needs no path")
}

internal fun artifactAt(path: String): Artifact = Samples.artifact.copy(ref = ArtifactRef(repositoryId, RepoPath(path)))

internal fun nodeAt(path: String): NodeRef = NodeRef.OfArtifact(artifactAt(path).ref)

/** What the producer emitted for one repository unit. */
internal class Emitted(val batches: List<FactBatch>) {
    val facts: List<Fact> get() = batches.flatMap { it.facts }
    val metrics: List<Metric> get() = facts.mapNotNull { (it as? Fact.OfMetric)?.value }
    val relationships: List<Relationship> get() = facts.mapNotNull { (it as? Fact.OfRelationship)?.value }
    val diagnostics: List<Diagnostic> get() = facts.mapNotNull { (it as? Fact.OfDiagnostic)?.value }

    fun metricOf(path: String, name: String): Double? =
        metrics.singleOrNull { it.node == nodeAt(path) && it.name == name }?.value

    fun relationshipsFrom(path: String): List<Relationship> = relationships.filter { it.from == nodeAt(path) }
}

/** Runs [GitHistoryProducer] over [inventory] as the engine would: one unit for the repository and a checkout. */
internal fun produceHistory(
    inventory: Collection<String>,
    root: Path,
    head: String?,
    producer: GitHistoryProducer = GitHistoryProducer(),
): Emitted {
    val unit = AnalysisUnit(owner, inventory.map(::artifactAt), null, null)
    val checkout = RepositoryCheckout(Repository(repositoryId, "synthetic", head, null), root)
    val request = ProduceRequest(
        units = listOf(unit),
        model = BuildModel(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        files = NoFiles,
        syntheticSources = emptyList(),
        artifacts = unit.artifacts,
        checkout = checkout,
    )
    val batches = mutableListOf<FactBatch>()
    producer.produce(request, FactSink { batches += it })
    return Emitted(batches)
}

internal fun SyntheticRepository.produce(workspace: Path = directory): Emitted =
    produceHistory(files(workspace), workspace, head())

internal const val COMMITS = HistorySignals.COMMITS
internal const val AUTHORS = HistorySignals.AUTHORS
internal const val RECENCY = HistorySignals.RECENCY_RANK

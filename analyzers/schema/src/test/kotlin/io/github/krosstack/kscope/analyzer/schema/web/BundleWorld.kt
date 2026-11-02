package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.FakeGraph
import io.github.krosstack.kscope.analyzer.schema.generator.GeneratorWorkspace
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.web.copy.CopySourceProducer
import io.github.krosstack.kscope.analyzer.schema.web.link.BundleConsumerLinker
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.WorkspaceFiles

/**
 * Several repositories with Gradle scripts and web files, in checkouts of a temporary directory, and what the producers
 * of the copy sources and of the web files and the linker of the bundles make of them, as a scan would run them: the
 * copies over the build scripts of the workspace, the web files over the modules that hold them, and the linker over
 * a graph of the facts of both.
 */
internal class BundleWorld(repositories: List<RepositorySpec>) : AutoCloseable {
    val workspace = GeneratorWorkspace(repositories)

    val copyBatches: List<FactBatch> by lazy { produceCopies(workspace.files) }

    val webFacts: List<Fact> by lazy { webBatches().flatMap { it.facts } }

    val copyFacts: List<Fact> get() = copyBatches.flatMap { it.facts }

    fun produceCopies(files: WorkspaceFiles): List<FactBatch> {
        val producer = CopySourceProducer()
        val batches = mutableListOf<FactBatch>()
        val request = workspace.request(producer.descriptor.consumes).copy(files = files)
        producer.produce(request) { batches += it }
        return batches
    }

    fun linkBatches(): List<FactBatch> {
        val graph = FakeGraph(copyFacts + webFacts, workspace.model, workspace.artifacts)
        val batches = mutableListOf<FactBatch>()
        BundleConsumerLinker().link(graph) { batches += it }
        return batches
    }

    fun links(): List<Relationship> = linkBatches().flatMap { it.facts }.relationships()

    override fun close() = workspace.close()

    private fun webBatches(): List<FactBatch> {
        val web = workspace.artifacts.filter { it.type in WebArtifactTypes.all }
        val units = web.groupBy { it.ref.repository }.map { (repository, members) ->
            AnalysisUnit(OwnershipUnit(OwnershipKind.REPOSITORY, repository.value), members, null, null)
        }
        val batches = mutableListOf<FactBatch>()
        WebBundleProducer().produce(workspace.request(emptySet()).copy(units = units)) { batches += it }
        return batches
    }
}

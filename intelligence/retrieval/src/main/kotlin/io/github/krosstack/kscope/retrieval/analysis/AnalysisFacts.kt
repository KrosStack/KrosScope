package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedModule
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.RiskFact
import io.github.krosstack.kscope.retrieval.nodeKey
import io.github.krosstack.kscope.retrieval.target

internal class AnalysisFacts(private val graph: RetrievalGraph) {
    fun scope(files: Set<ArtifactRef>): ChangeScope {
        val modules = files.mapNotNull(graph::module).distinct().sortedBy { it.value }
        val sets = files.mapNotNull { graph.artifacts[it]?.sourceSet }.distinct().sortedBy { it.value }
        val targets = sets.flatMap { graph.sourceSets[it]?.targets.orEmpty() }.toSet()
        val variants = sets.flatMap { graph.sourceSets[it]?.variants.orEmpty() }.toSet()
        val public = graph.symbols.values.filter {
            it.declaration?.artifact in files &&
                it.visibility == Visibility.PUBLIC
        }
            .map { NodeRef.OfSymbol(it.id) }.sortedBy(::nodeKey)
        return ChangeScope(
            modules,
            sets,
            graph.structure.targets.filter {
                it.id in targets
            }.map { it.platform.name.lowercase() }.distinct().sorted(),
            graph.structure.variants.filter { it.id in variants }.map { it.name }.distinct().sorted(),
            public,
            modules.size > 1,
        )
    }

    fun near(files: Set<ArtifactRef>): List<Relationship> {
        val nodes = graph.nodes(files)
        return graph.edges.filter { it.from in nodes || it.target() in nodes }
    }

    fun risks(files: Set<ArtifactRef>): List<RiskFact> {
        val ordered = files.sortedBy(graph.locations::pathOf)
        val nearby = nearToEachFile(ordered)
        return ordered.flatMap { file ->
            val node = NodeRef.OfArtifact(file)
            val edges = nearby.getValue(file)
            listOf(
                RiskFact(node, "fan-in", edges.count { enters(it, file) }.toDouble()),
                RiskFact(node, "fan-out", edges.count { leaves(it, file) }.toDouble()),
                RiskFact(node, "size.lines", graph.artifacts[file]?.lineCount?.toDouble() ?: 0.0),
                RiskFact(node, "unresolved", edges.count { it.target() == null }.toDouble()),
                RiskFact(node, "test-relationships", edges.count { it.kind == RelationKind.TESTS }.toDouble()),
            ) + graph.reader.metrics(node).map { RiskFact(node, it.name, it.value) }
        }
    }

    private fun nearToEachFile(files: List<ArtifactRef>): Map<ArtifactRef, List<Relationship>> {
        val owner = HashMap<NodeRef, ArtifactRef>()
        files.forEach { file -> graph.nodes(setOf(file)).forEach { owner[it] = file } }
        val found = files.associateWith { mutableListOf<Relationship>() }
        graph.edges.forEach { edge ->
            val source = owner[edge.from]
            val target = edge.target()?.let(owner::get)
            source?.let { found.getValue(it).add(edge) }
            target?.takeIf { it != source }?.let { found.getValue(it).add(edge) }
        }
        return found
    }

    private fun enters(edge: Relationship, file: ArtifactRef): Boolean =
        graph.file(edge.from) != file && edge.target()?.let(graph::file) == file

    private fun leaves(edge: Relationship, file: ArtifactRef): Boolean =
        graph.file(edge.from) == file && edge.target()?.let { graph.file(it) != file } == true

    fun modules(files: List<RankedFile>): List<RankedModule> = files.mapNotNull { file ->
        graph.module(file.artifact)?.let { it to file.score }
    }.groupBy({ it.first }, { it.second }).map { (id, scores) ->
        RankedModule(id, graph.structure.modules.first { it.id == id }.path, scores.sum())
    }.sortedWith(compareByDescending<RankedModule> { it.score }.thenBy { it.id.value })

    companion object {
        val BOUNDARIES = setOf(RelationKind.BINDS, RelationKind.GENERATES, RelationKind.ACTUALIZES)
    }
}

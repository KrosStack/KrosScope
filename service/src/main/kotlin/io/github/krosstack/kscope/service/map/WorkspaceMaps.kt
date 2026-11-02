package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.HistorySignals
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.map.tree.MapFacts
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/**
 * The map of the workspace as the index holds it now. It loads the index when that has not happened, and fails with
 * [io.github.krosstack.kscope.service.NotIndexedException] when there is none.
 */
public fun KrosScope.workspaceMap(): WorkspaceMap {
    memory.load()
    val graph = graph()
    val commits = graph.metrics(HistorySignals.COMMITS).mapNotNull { metric ->
        (metric.node as? NodeRef.OfArtifact)?.let { it.ref to metric.value.toInt() }
    }.toMap()
    val facts = MapFacts(
        workspace = workspaceRoot.fileName?.toString() ?: "workspace",
        level = memory.levelMemory.level()?.wireName,
        structure = graph.structure(),
        artifacts = graph.artifacts(),
        symbols = graph.symbols(SymbolQuery(limit = Int.MAX_VALUE)),
        relationships = graph.relationships(RelationshipQuery(limit = Int.MAX_VALUE)),
        commits = commits,
    )
    return WorkspaceMapBuilder(facts).build()
}

/**
 * What a change to a node or to a symbol of [map] reaches. A question that has no answer returns a value with
 * [MapImpact.error]. Runs on the thread of the caller, like every use case.
 */
public fun KrosScope.mapImpact(map: WorkspaceMap, nodeId: String? = null, symbolId: String? = null): MapImpact =
    MapImpacts(map).answer(this, nodeId, symbolId)

/** Ranks the files, symbols and modules of [map] for the task in [text]. */
public fun KrosScope.mapTask(map: WorkspaceMap, text: String): MapTask =
    MapTasks(map).of(analyze(text, includeContextEstimates = false))

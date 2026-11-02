package io.github.krosstack.kscope.service.map.tree

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

/** What the map is built from; the lists are read and never changed. */
internal data class MapFacts(
    val workspace: String,
    val level: String?,
    val structure: WorkspaceStructure,
    val artifacts: List<Artifact>,
    val symbols: List<Symbol>,
    val relationships: List<Relationship>,
    val commits: Map<ArtifactRef, Int> = emptyMap(),
)

package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.graph.GraphReader

/** What an indexing job may ask of its workspace; every call belongs to the owner thread of the workspace. */
internal class SemanticWorkspace(
    val indexed: () -> Boolean,
    val graph: () -> GraphReader,
    val files: () -> WorkspaceFiles,
    val revision: () -> Long,
    val refresh: () -> Boolean,
)

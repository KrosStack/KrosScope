package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal class StructuredFixture(private val base: ContextFixture, private val workspace: WorkspaceStructure) :
    GraphReader by base,
    WorkspaceFiles by base {
    override fun structure(): WorkspaceStructure = workspace
}

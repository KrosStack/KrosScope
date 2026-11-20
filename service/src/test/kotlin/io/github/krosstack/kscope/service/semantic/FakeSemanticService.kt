package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import java.nio.file.Path

/** Opens workspaces with a recording fake in place of the installed worker, for tests of other modules. */
class FakeSemanticService(scratch: Path) {
    private val components = FakeSemanticComponents(scratch)

    val componentsOpened: Int get() = components.opened
    val workersStarted: Int get() = components.starts
    val workersClosed: Int get() = components.closed
    val textsEmbedded: Int get() = components.provider.embedded.size
    val queriesEmbedded: Int get() = components.queriesEmbedded.size

    fun open(root: Path, options: OpenOptions): KrosScope =
        KrosScope.open(root, options, { null }, semanticComponents = components)
}

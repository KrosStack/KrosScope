package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.IndexFiles
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files

/** Whether the index of this engine was built with [scope]; false when there is no index. */
public fun ScanEngine.isIndexedWith(scope: ScanScope): Boolean {
    if (!Files.isRegularFile(IndexFiles(config.indexDirectory).database)) return false
    val stored = (graph() as GraphStore).units().firstOrNull { it.producer == ScopeUnit.producer }?.fingerprint
    return stored == ScopeUnit.keyOf(scope)
}

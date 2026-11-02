package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.IndexFiles
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files

/** Whether the index of this engine was built with exactly the analyzer groups [disabled] switched off. */
public fun ScanEngine.isIndexedWithout(disabled: Set<String>): Boolean {
    if (!Files.isRegularFile(IndexFiles(config.indexDirectory).database)) return false
    val stored = (graph() as GraphStore).units().firstOrNull { it.producer == AnalyzerUnit.producer }?.fingerprint
    return stored == AnalyzerUnit.keyOf(disabled)
}

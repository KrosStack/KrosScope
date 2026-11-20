package io.github.krosstack.kscope.cli.serve.semantic

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.advanceSemanticIndex
import io.github.krosstack.kscope.service.semantic.semanticNotice

internal class ServeSemantics(
    private val current: () -> KrosScope,
    private val loaded: () -> Boolean,
    ml: MlMode,
    private val workerMemoryMib: () -> Long?,
) {
    private var mode = if (ml == MlMode.ON) SemanticIndexingMode.DEFERRED else SemanticIndexingMode.OFF

    fun openOptions() = OpenOptions(semanticIndexing = mode, workerMemoryMib = workerMemoryMib())

    fun forScan(options: OpenOptions): OpenOptions {
        mode = if (options.semanticIndexing == SemanticIndexingMode.OFF) {
            SemanticIndexingMode.OFF
        } else {
            SemanticIndexingMode.DEFERRED
        }
        return options.copy(semanticIndexing = mode)
    }

    fun automaticScanArguments(): List<String> =
        if (mode == SemanticIndexingMode.OFF) listOf("--no-ml") else emptyList()

    fun advance() {
        if (loaded()) current().advanceSemanticIndex()
    }

    fun notice(): String? = if (loaded() && mode != SemanticIndexingMode.OFF) current().semanticNotice() else null
}

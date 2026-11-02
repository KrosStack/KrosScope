package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.spi.FactProducer

/** The capabilities that read the version-control history of the workspace. */
public object HistoryCapabilities {
    public fun producers(): List<FactProducer> = listOf(GitHistoryProducer())
}

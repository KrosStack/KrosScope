package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.engine.scope.ScanScope

/** What `info` knows of the index when it names the state of the scan settings and of the analyzer switches. */
internal class IndexedScope(
    private val indexed: Boolean,
    private val builtWith: (ScanScope) -> Boolean,
    private val builtWithout: (Set<String>) -> Boolean,
) {
    fun stateOf(scope: ScanScope): String = stateOf { builtWith(scope) }

    fun stateOf(analyzers: AnalyzerSettings): String = stateOf { builtWithout(analyzers.disabledKeys()) }

    private fun stateOf(current: () -> Boolean): String = when {
        !indexed -> "not-indexed"
        current() -> "current"
        else -> "outdated"
    }

    companion object {
        val NOT_INDEXED = IndexedScope(false, { false }, { false })
    }
}

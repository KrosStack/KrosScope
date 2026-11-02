package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.settings.AnalyzerSettings
import io.github.krosstack.kscope.spi.CapabilityRegistry

/**
 * Builds the registry of the analyzer groups that are on at the level of the scan, and builds it again only when the
 * groups or the level change.
 */
internal class SelectedRegistry(
    private val selection: () -> AnalyzerSettings,
    private val level: () -> ScanLevel,
    private val build: (AnalyzerSettings, ScanLevel) -> CapabilityRegistry,
) {
    private var last: Triple<AnalyzerSettings, ScanLevel, CapabilityRegistry>? = null

    @Synchronized
    fun get(): CapabilityRegistry {
        val wanted = selection()
        val depth = level()
        last?.takeIf { it.first == wanted && it.second == depth }?.let { return it.third }
        return build(wanted, depth).also { last = Triple(wanted, depth, it) }
    }
}

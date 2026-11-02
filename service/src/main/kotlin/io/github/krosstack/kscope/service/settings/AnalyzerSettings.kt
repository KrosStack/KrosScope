package io.github.krosstack.kscope.service.settings

/** The analyzer groups that a scan leaves out. Every group is on unless it is named in [disabled]. */
public data class AnalyzerSettings(public val disabled: Set<AnalyzerKind> = emptySet()) {
    public fun isEnabled(kind: AnalyzerKind): Boolean = kind !in disabled

    internal fun disabledKeys(): Set<String> = disabled.map { it.key }.toSet()

    public companion object {
        public val ALL: AnalyzerSettings = AnalyzerSettings()
    }
}

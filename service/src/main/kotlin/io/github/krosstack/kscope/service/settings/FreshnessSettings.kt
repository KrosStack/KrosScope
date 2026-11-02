package io.github.krosstack.kscope.service.settings

/**
 * How the resident service keeps an index fresh: whether it watches the workspace and how long the files must stay
 * the same before it updates. `kscope serve` keeps its own flag and does not read [watch].
 */
public data class FreshnessSettings(public val watch: Boolean = true, public val quietSeconds: Int = DEFAULT_QUIET) {
    public companion object {
        public const val DEFAULT_QUIET: Int = 3
    }
}

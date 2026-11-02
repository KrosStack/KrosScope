package io.github.krosstack.kscope.engine.build

/**
 * Whether the version a consumer pins is the version the workspace builds from source. A built version that is
 * `unspecified` was not read, so nothing is compared with it.
 */
internal enum class VersionState(val value: String) {
    MATCH("true"),
    MISMATCH("false"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun of(pinned: String?, built: String): VersionState = when {
            built == UNSPECIFIED || pinned == null || isDynamic(pinned) -> UNKNOWN
            pinned == built -> MATCH
            else -> MISMATCH
        }

        fun isDynamic(version: String) = version.isBlank() || version.endsWith("+") ||
            version.startsWith("latest.") || version.any { it in RANGE_CHARACTERS }

        private const val RANGE_CHARACTERS = "[](),"
        private const val UNSPECIFIED = "unspecified"
    }
}

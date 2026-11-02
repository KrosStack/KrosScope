package io.github.krosstack.kscope.cli.text.format

/** How much text a ranked result prints: one screen, the usual sections, or every section up to the cut-off. */
internal enum class DetailLevel {
    BRIEF,
    STANDARD,
    FULL,
    ;

    /** The rows a list may show: [standard] by default, [BRIEF_ROWS] for a brief result and all of them when full. */
    fun rows(standard: Int): Int = when (this) {
        BRIEF -> BRIEF_ROWS
        STANDARD -> standard
        FULL -> Int.MAX_VALUE
    }

    companion object {
        const val BRIEF_ROWS = 5
    }
}

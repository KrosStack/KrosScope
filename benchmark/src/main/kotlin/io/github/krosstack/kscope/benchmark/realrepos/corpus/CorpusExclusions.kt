package io.github.krosstack.kscope.benchmark.realrepos.corpus

internal object CorpusExclusions {
    const val MISSING_PARENT = "missing-parent"
    const val BOT = "bot"
    const val FORMATTING = "formatting"
    const val NO_PARENT_SOURCE_FILES = "no-parent-source-files"
    const val CHANGED_SOURCE_TYPE = "changed-source-type"
    const val TOO_MANY_SOURCE_FILES = "too-many-source-files"
    const val PURE_RENAME = "pure-rename"
    const val WHITESPACE_ONLY = "whitespace-only"
    const val EMPTY_STATEMENT = "empty-statement"
    const val SELECTION_LIMIT = "selection-limit"

    val reasons: Set<String> = setOf(
        MISSING_PARENT,
        BOT,
        FORMATTING,
        NO_PARENT_SOURCE_FILES,
        CHANGED_SOURCE_TYPE,
        TOO_MANY_SOURCE_FILES,
        PURE_RENAME,
        WHITESPACE_ONLY,
        EMPTY_STATEMENT,
        SELECTION_LIMIT,
    )
}

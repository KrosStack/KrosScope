package io.github.krosstack.kscope.cli.help

/** The options that every command accepts; a command's help lists only the ones that it honours. */
internal enum class SharedOption {
    WORKSPACE,
    FORMAT,
    PROGRESS,
    QUIET,
    DETAIL,
    SECTIONS,
    NO_SERVER,
    ;

    companion object {
        val ALL: Set<SharedOption> = entries.toSet()
        val WORKSPACE_ONLY: Set<SharedOption> = setOf(WORKSPACE)
        val QUERY: Set<SharedOption> = setOf(WORKSPACE, FORMAT, PROGRESS, QUIET, NO_SERVER)
        val PRESENTED_QUERY: Set<SharedOption> = QUERY + setOf(DETAIL, SECTIONS)
    }
}

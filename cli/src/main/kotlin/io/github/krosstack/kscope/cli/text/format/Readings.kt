package io.github.krosstack.kscope.cli.text.format

/** The first line of an `analyze` result: which reading of the argument was used and how to get the other. */
internal object Readings {
    private const val QUERY_WIDTH = 60

    const val TASK = "Read as a task (use --as symbol to look it up as a name)."

    fun taskAfterMiss(requested: String): String =
        "Nothing is named \"${Format.clip(requested, QUERY_WIDTH)}\", so it was read as a task " +
            "(use --as symbol to look for a name only)."

    fun name(requested: String): String =
        "Read \"${Format.clip(requested, QUERY_WIDTH)}\" as a name (use --as task to analyse it as a task)."

    fun file(requested: String): String =
        "Read \"${Format.clip(requested, QUERY_WIDTH)}\" as a file (use --as task to analyse it as a task)."

    const val NAME_REQUESTED = "Looked up as a name, as requested (use --as task to analyse it as a task)."
}

package io.github.krosstack.kscope.conventions

internal object GitTestEnvironment {
    fun isolated(source: Map<String, Any>): Map<String, Any> =
        source.filterKeys { !it.startsWith("GIT_", ignoreCase = true) } + identity + configuration

    private val identity = mapOf(
        "GIT_CONFIG_GLOBAL" to if (System.getProperty("os.name").startsWith("Windows")) "NUL" else "/dev/null",
        "GIT_CONFIG_NOSYSTEM" to "1",
        "GIT_AUTHOR_NAME" to "Test",
        "GIT_AUTHOR_EMAIL" to "test@example.invalid",
        "GIT_COMMITTER_NAME" to "Test",
        "GIT_COMMITTER_EMAIL" to "test@example.invalid",
        "GIT_TERMINAL_PROMPT" to "0",
    )

    private val settings = mapOf(
        "commit.gpgsign" to "false",
        "tag.gpgsign" to "false",
        "gc.auto" to "0",
        "gc.autoDetach" to "false",
        "maintenance.auto" to "false",
        "maintenance.autoDetach" to "false",
    )

    private val configuration = mapOf("GIT_CONFIG_COUNT" to settings.size.toString()) +
        settings.entries.flatMapIndexed { index, (name, value) ->
            listOf("GIT_CONFIG_KEY_$index" to name, "GIT_CONFIG_VALUE_$index" to value)
        }.toMap()
}

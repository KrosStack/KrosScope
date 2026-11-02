package io.github.krosstack.kscope.benchmark.git

import java.util.Locale

internal object RepositorySelection {
    fun cleared(environment: Map<String, String>): Map<String, String> = environment.filterKeys { !selects(it) }

    fun applyTo(builder: ProcessBuilder, environment: Map<String, String>) {
        builder.environment().clear()
        builder.environment().putAll(cleared(environment))
    }

    private fun selects(name: String): Boolean = name.uppercase(Locale.ROOT) in SELECTORS

    private val SELECTORS = setOf(
        "GIT_DIR",
        "GIT_WORK_TREE",
        "GIT_INDEX_FILE",
        "GIT_OBJECT_DIRECTORY",
        "GIT_ALTERNATE_OBJECT_DIRECTORIES",
        "GIT_COMMON_DIR",
        "GIT_NAMESPACE",
        "GIT_CEILING_DIRECTORIES",
    )
}

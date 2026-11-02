package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.git.RepositorySelection
import java.util.Locale

internal object ChildEnvironment {
    fun without(prefixes: List<String>, source: Map<String, String> = System.getenv()): Map<String, String> =
        source.filterKeys { name -> prefixes.none { name.uppercase(Locale.ROOT).startsWith(it) } }

    fun with(source: Map<String, String>, additions: Map<String, String>): Map<String, String> {
        val result = source.toMutableMap()
        additions.forEach { (name, value) ->
            result.keys.filter { it.equals(name, ignoreCase = true) }.forEach(result::remove)
            result[name] = value
        }
        return result
    }

    fun isolatedGit(source: Map<String, String> = System.getenv()): Map<String, String> = with(
        RepositorySelection.cleared(source),
        mapOf(
            "GIT_CONFIG_GLOBAL" to nullDevice(),
            "GIT_CONFIG_NOSYSTEM" to "1",
            "GIT_TERMINAL_PROMPT" to "0",
            "GIT_AUTHOR_NAME" to "benchmark",
            "GIT_AUTHOR_EMAIL" to "benchmark@example.invalid",
            "GIT_COMMITTER_NAME" to "benchmark",
            "GIT_COMMITTER_EMAIL" to "benchmark@example.invalid",
            "GIT_AUTHOR_DATE" to FIXED_DATE,
            "GIT_COMMITTER_DATE" to FIXED_DATE,
        ),
    )

    private fun nullDevice(): String = if (isWindows()) "NUL" else "/dev/null"

    private const val FIXED_DATE = "2000-01-01T00:00:00Z"
}

internal fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

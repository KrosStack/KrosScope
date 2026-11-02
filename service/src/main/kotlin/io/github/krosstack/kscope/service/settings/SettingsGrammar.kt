package io.github.krosstack.kscope.service.settings

/** The keys that this version of the settings file accepts. Every other key is an error. */
internal object SettingsGrammar {
    const val MAXIMUM_PATTERNS = 256

    val FORMAT = listOf("format")
    val RESOURCES = listOf("resources")
    val WORKERS = RESOURCES + "workers"
    val WORKER_HEAP_MIB = RESOURCES + "worker-heap-mib"
    val SCAN = listOf("scan")
    val INCLUDE = SCAN + "include"
    val EXCLUDE = SCAN + "exclude"
    val GENERATED = SCAN + "generated"
    val TESTS = SCAN + "tests"
    val ANALYZERS = listOf("analyzers")
    val FRESHNESS = listOf("freshness")
    val WATCH = FRESHNESS + "watch"
    val QUIET_SECONDS = FRESHNESS + "quiet-seconds"
    val SWITCHES: List<List<String>> = AnalyzerKind.entries.map { ANALYZERS + it.key }
    val LEVEL = listOf("level")

    val INTEGERS: Map<List<String>, IntRange> = linkedMapOf(
        FORMAT to 1..1,
        WORKERS to 1..8,
        WORKER_HEAP_MIB to 256..8192,
        QUIET_SECONDS to 1..300,
    )

    val PATTERN_LISTS: List<List<String>> = listOf(INCLUDE, EXCLUDE, GENERATED)

    val BOOLEANS: List<List<String>> = listOf(TESTS, WATCH) + SWITCHES

    val TABLES: List<List<String>> = listOf(RESOURCES, SCAN, ANALYZERS, FRESHNESS)

    val VALUES: Set<List<String>> = INTEGERS.keys + PATTERN_LISTS + BOOLEANS + setOf(LEVEL)

    val KNOWN: Set<List<String>> = VALUES + TABLES
}

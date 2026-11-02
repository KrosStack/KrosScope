package io.github.krosstack.kscope.cli.service.home

import org.tomlj.TomlParseResult

internal object ServiceFileLimits {
    const val IDLE_KEY = "idle-minutes"
    const val MEMORY_KEY = "memory-mib"
    val KEYS = listOf(IDLE_KEY, MEMORY_KEY)

    fun problem(toml: TomlParseResult): String? = KEYS.firstNotNullOfOrNull { key ->
        val value = toml.get(listOf(key))
        if (value == null || (value is Long && value >= 0)) {
            null
        } else {
            "\"$key\" must be an integer of 0 or more (${toml.inputPositionOf(listOf(key))})"
        }
    }

    fun of(toml: TomlParseResult): ServiceLimits =
        ServiceLimits(toml.get(listOf(IDLE_KEY)) as? Long, toml.get(listOf(MEMORY_KEY)) as? Long)
}

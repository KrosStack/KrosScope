package io.github.krosstack.kscope.benchmark

import java.nio.file.Path

internal object AgentDefaults {
    fun tasks(): String = absolute((System.getProperty("kscope.gold") ?: "benchmark/reference") + "/tasks")

    fun workspace(): String = absolute(System.getProperty("kscope.fixture") ?: "fixtures/reference-workspace")

    private fun absolute(path: String): String = Path.of(path).toAbsolutePath().normalize().toString()
}

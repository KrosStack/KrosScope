package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationSuiteResult
import kotlinx.serialization.json.Json

public object BenchmarkJson {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    public fun encode(result: LocalizationSuiteResult): String = json.encodeToString(result)

    public fun decodeLocalization(text: String): LocalizationSuiteResult = json.decodeFromString(text)

    public fun encode(result: ContextBenchmarkResult): String = json.encodeToString(result)
}

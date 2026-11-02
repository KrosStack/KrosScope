package io.github.krosstack.kscope.service.wiring

import java.util.Properties

internal object ToolVersion {
    private const val RESOURCE = "/kscope-version.properties"
    private const val UNKNOWN = "unknown"

    val value: String = ToolVersion::class.java.getResourceAsStream(RESOURCE)
        ?.use { input -> Properties().apply { load(input) }.getProperty("version") }
        ?: UNKNOWN
}

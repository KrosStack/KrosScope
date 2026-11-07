package io.github.krosstack.kscope.service.components

import kotlinx.serialization.Serializable

@Serializable
public data class ComponentFile(
    public val path: String,
    public val platform: String,
    public val urls: List<String>,
    public val size: Long,
    public val sha256: String,
)

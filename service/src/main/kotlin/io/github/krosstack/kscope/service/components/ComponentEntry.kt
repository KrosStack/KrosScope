package io.github.krosstack.kscope.service.components

import kotlinx.serialization.Serializable

@Serializable
public data class ComponentEntry(
    public val name: String,
    public val version: String,
    public val licence: String,
    public val licenceFile: String,
    public val description: String,
    public val files: List<ComponentFile>,
)

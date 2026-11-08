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
    public val platforms: List<String>? = null,
) {
    internal fun supports(platform: String): Boolean =
        (platforms == null || platform in platforms) && files.any { it.platform == "any" || it.platform == platform }
}

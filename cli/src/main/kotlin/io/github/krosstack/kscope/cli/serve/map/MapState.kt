package io.github.krosstack.kscope.cli.serve.map

import kotlinx.serialization.Serializable

/** What a viewer is told over the event stream; a change of [version] means that the map is to be read again. */
@Serializable
internal data class MapState(
    val version: Int,
    val indexed: Boolean,
    val level: String?,
    val scanning: Boolean,
    val watching: Boolean,
    val watch: String?,
)

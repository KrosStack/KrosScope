package io.github.krosstack.kscope.diagram

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class DiagramNode(
    public val id: String,
    public val label: String,
    public val kind: String,
    public val group: String?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val selected: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val symbolId: String? = null,
)

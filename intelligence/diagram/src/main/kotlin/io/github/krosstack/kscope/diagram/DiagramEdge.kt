package io.github.krosstack.kscope.diagram

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class DiagramEdge(
    public val from: String,
    public val to: String,
    public val label: String,
    public val weakest: DrawnState,
    public val byState: Map<DrawnState, Int>,
    public val byEvidence: Map<String, Int>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val position: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val byKind: Map<String, Int> = emptyMap(),
)

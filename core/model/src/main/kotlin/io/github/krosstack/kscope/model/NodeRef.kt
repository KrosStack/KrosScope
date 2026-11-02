package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface NodeRef {
    @Serializable
    @SerialName("symbol")
    public data class OfSymbol(public val id: SymbolId) : NodeRef

    @Serializable
    @SerialName("artifact")
    public data class OfArtifact(public val ref: ArtifactRef) : NodeRef

    @Serializable
    @SerialName("module")
    public data class OfModule(public val id: ModuleId) : NodeRef

    @Serializable
    @SerialName("source-set")
    public data class OfSourceSet(public val id: SourceSetId) : NodeRef

    /** A dependency outside the workspace, identified by a coordinate such as `maven:group:artifact`. */
    @Serializable
    @SerialName("external")
    public data class OfExternal(public val coordinate: String) : NodeRef
}

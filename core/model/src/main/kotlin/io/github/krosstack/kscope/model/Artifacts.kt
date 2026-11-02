package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class ArtifactCategory {
    @SerialName("source")
    SOURCE,

    @SerialName("build")
    BUILD,

    @SerialName("resource")
    RESOURCE,

    @SerialName("schema")
    SCHEMA,

    @SerialName("config")
    CONFIG,

    @SerialName("native")
    NATIVE,

    @SerialName("documentation")
    DOCUMENTATION,

    @SerialName("other")
    OTHER,
}

/** The id is open and namespaced, for example `kotlin.source` or `android.manifest`. */
@Serializable
public data class ArtifactType(public val id: String, public val category: ArtifactCategory)

@Serializable
public data class GeneratedInfo(public val generator: String, public val sourceOfTruth: NodeRef?)

@Serializable
public data class Artifact(
    public val ref: ArtifactRef,
    public val type: ArtifactType,
    public val contentHash: ContentHash,
    public val sizeBytes: Long,
    public val lineCount: Int,
    public val sourceSet: SourceSetId?,
    public val generated: GeneratedInfo?,
)

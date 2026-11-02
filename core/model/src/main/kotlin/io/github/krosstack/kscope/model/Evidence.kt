package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class EvidenceClass {
    @SerialName("compiler")
    COMPILER,

    @SerialName("build-model")
    BUILD_MODEL,

    @SerialName("generated-artifact")
    GENERATED_ARTIFACT,

    /** The content of a built output that a producer read, such as the class list of a JAR. */
    @SerialName("content")
    CONTENT,

    @SerialName("convention")
    CONVENTION,

    @SerialName("structural")
    STRUCTURAL,

    @SerialName("textual")
    TEXTUAL,

    @SerialName("historical")
    HISTORICAL,

    @SerialName("ml")
    ML,
}

@Serializable
public enum class Certainty {
    @SerialName("exact")
    EXACT,

    @SerialName("probable")
    PROBABLE,

    @SerialName("heuristic")
    HEURISTIC,
}

/** Namespaced, for example `kotlin.unresolved-reference` or `jni.native-side-not-analysed`. */
@Serializable
@JvmInline
public value class ReasonCode(public val value: String)

@Serializable
public sealed interface Resolution {
    @Serializable
    @SerialName("resolved")
    public data class Resolved(public val target: NodeRef, public val certainty: Certainty) : Resolution

    /** The producer looked for the target and did not find it. */
    @Serializable
    @SerialName("unresolved")
    public data class Unresolved(public val hint: String, public val reason: ReasonCode) : Resolution

    /** The producer did not look for the target at all. */
    @Serializable
    @SerialName("not-analysed")
    public data class NotAnalysed(public val hint: String, public val reason: ReasonCode) : Resolution
}

/** A null component means the fact holds everywhere along that dimension. */
@Serializable
public data class Scope(
    public val sourceSet: SourceSetId? = null,
    public val target: TargetId? = null,
    public val variant: VariantId? = null,
)

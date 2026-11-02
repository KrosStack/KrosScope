package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ProducerRef(public val id: String, public val version: String)

@Serializable
public enum class OwnershipKind {
    @SerialName("workspace")
    WORKSPACE,

    @SerialName("repository")
    REPOSITORY,

    @SerialName("build-root")
    BUILD_ROOT,

    @SerialName("module")
    MODULE,

    @SerialName("source-set")
    SOURCE_SET,

    @SerialName("artifact")
    ARTIFACT,
}

@Serializable
public data class OwnershipUnit(public val kind: OwnershipKind, public val key: String)

@Serializable
public enum class DiagnosticSeverity {
    @SerialName("info")
    INFO,

    @SerialName("warning")
    WARNING,

    @SerialName("error")
    ERROR,
}

@Serializable
public data class Diagnostic(
    public val severity: DiagnosticSeverity,
    public val code: ReasonCode,
    public val message: String,
    public val location: SourceLocation?,
)

@Serializable
public sealed interface Fact {
    @Serializable
    @SerialName("repository")
    public data class OfRepository(public val value: Repository) : Fact

    @Serializable
    @SerialName("build-root")
    public data class OfBuildRoot(public val value: BuildRoot) : Fact

    @Serializable
    @SerialName("module")
    public data class OfModule(public val value: Module) : Fact

    @Serializable
    @SerialName("target")
    public data class OfTarget(public val value: Target) : Fact

    @Serializable
    @SerialName("variant")
    public data class OfVariant(public val value: Variant) : Fact

    @Serializable
    @SerialName("source-set")
    public data class OfSourceSet(public val value: SourceSet) : Fact

    @Serializable
    @SerialName("artifact")
    public data class OfArtifact(public val value: Artifact) : Fact

    @Serializable
    @SerialName("symbol")
    public data class OfSymbol(public val value: Symbol) : Fact

    @Serializable
    @SerialName("relationship")
    public data class OfRelationship(public val value: Relationship) : Fact

    @Serializable
    @SerialName("symbolic-reference")
    public data class OfSymbolicReference(public val value: SymbolicReference) : Fact

    @Serializable
    @SerialName("metric")
    public data class OfMetric(public val value: Metric) : Fact

    @Serializable
    @SerialName("diagnostic")
    public data class OfDiagnostic(public val value: Diagnostic) : Fact
}

/** Everything one producer derived from one ownership unit. Replaced atomically. */
@Serializable
public data class FactBatch(
    public val producer: ProducerRef,
    public val owner: OwnershipUnit,
    public val inputFingerprint: Fingerprint,
    public val facts: List<Fact>,
)

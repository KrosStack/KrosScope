package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class SourceSetRole {
    @SerialName("main")
    MAIN,

    @SerialName("unit-test")
    UNIT_TEST,

    @SerialName("instrumented-test")
    INSTRUMENTED_TEST,

    @SerialName("test-fixtures")
    TEST_FIXTURES,
}

/** Open and namespaced: `kotlin`, `java`, `android.res`, `android.manifest`, `aidl`. */
@Serializable
@JvmInline
public value class SourceRootKind(public val value: String)

@Serializable
public data class SourceRoot(
    public val directory: RepoPath,
    public val kind: SourceRootKind,
    public val generated: Boolean,
)

@Serializable
public data class SourceSet(
    public val id: SourceSetId,
    public val module: ModuleId,
    public val name: String,
    public val role: SourceSetRole,
    public val dependsOn: List<SourceSetId>,
    public val roots: List<SourceRoot>,
    public val targets: List<TargetId>,
    public val variants: List<VariantId>,
    public val evidenceClass: EvidenceClass,
    public val certainty: Certainty,
)

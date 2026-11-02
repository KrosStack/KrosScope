package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [directory] is where the checkout lies relative to the workspace root, empty when the root is the checkout. It is a
 * location, not part of the identity of the repository.
 */
@Serializable
public data class Repository(
    public val id: RepositoryId,
    public val name: String,
    public val headRevision: String?,
    public val originUrl: String?,
    public val directory: RepoPath = RepoPath(""),
)

@Serializable
public enum class BuildSystem {
    @SerialName("gradle")
    GRADLE,

    @SerialName("kotlin-toolchain")
    KOTLIN_TOOLCHAIN,

    @SerialName("none")
    NONE,
}

@Serializable
public data class BuildRoot(
    public val id: BuildRootId,
    public val repository: RepositoryId,
    public val directory: RepoPath,
    public val buildSystem: BuildSystem,
)

@Serializable
public enum class ModuleTrait {
    @SerialName("android-application")
    ANDROID_APPLICATION,

    @SerialName("android-library")
    ANDROID_LIBRARY,

    @SerialName("kotlin-multiplatform")
    KOTLIN_MULTIPLATFORM,

    @SerialName("kotlin-jvm")
    KOTLIN_JVM,

    @SerialName("kotlin-android")
    KOTLIN_ANDROID,

    @SerialName("java")
    JAVA,

    @SerialName("build-logic")
    BUILD_LOGIC,
}

/**
 * [attributes] are build facts that later phases consume, such as `android.compileSdk`, serialized sorted by key.
 * `maven.publications` lists every coordinate the module publishes as `group:artifact:version`, sorted and separated
 * by commas; `maven.heuristic` is `true` when they were read from a convention plugin or when the version of one of
 * them could not be read and is recorded as `unspecified`.
 */
@Serializable
public data class Module(
    public val id: ModuleId,
    public val buildRoot: BuildRootId,
    public val path: String,
    public val directory: RepoPath,
    public val plugins: List<String>,
    @Serializable(with = ModuleTraitSetSerializer::class)
    public val traits: Set<ModuleTrait>,
    public val androidNamespace: String?,
    public val evidenceClass: EvidenceClass,
    public val certainty: Certainty,
    @Serializable(with = SortedAttributesSerializer::class)
    public val attributes: Map<String, String> = emptyMap(),
)

@Serializable
public enum class Platform {
    @SerialName("common")
    COMMON,

    @SerialName("jvm")
    JVM,

    @SerialName("android")
    ANDROID,

    @SerialName("native")
    NATIVE,

    @SerialName("js")
    JS,

    @SerialName("wasm")
    WASM,
}

@Serializable
public data class Target(
    public val id: TargetId,
    public val module: ModuleId,
    public val name: String,
    public val platform: Platform,
)

@Serializable
public data class Variant(
    public val id: VariantId,
    public val module: ModuleId,
    public val name: String,
    public val buildType: String?,
    public val flavors: List<String>,
)

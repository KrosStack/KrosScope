package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.Serializable

@Serializable
public data class GoldTarget(public val name: String, public val platform: String)

/**
 * A source set with every directory it reads. [dependsOn] is empty for source sets that refine nothing. The
 * generated directories of a build are not part of gold, because they depend on whether the build ran.
 */
@Serializable
public data class GoldSourceSet(
    public val name: String,
    public val dependsOn: List<String> = emptyList(),
    public val sourceDirs: List<String> = emptyList(),
    public val resourceDirs: List<String> = emptyList(),
    public val manifest: String? = null,
)

@Serializable
public data class GoldAndroid(
    public val namespace: String? = null,
    public val compileSdk: Int? = null,
    public val minSdk: Int? = null,
    public val applicationId: String? = null,
)

/**
 * A module of the build. [appliedPlugins] are the plugin ids applied directly or implied by a plugin and
 * [conventionPlugins] the ids of the convention plugins of the build that it applies.
 */
@Serializable
public data class GoldModule(
    public val path: String,
    public val dir: String,
    public val kind: String,
    public val appliedPlugins: List<String> = emptyList(),
    public val conventionPlugins: List<String> = emptyList(),
    public val android: GoldAndroid? = null,
    public val targets: List<GoldTarget> = emptyList(),
    public val sourceSets: List<GoldSourceSet> = emptyList(),
    public val variants: List<String> = emptyList(),
)

@Serializable
public data class GoldProjectDependency(
    public val from: String,
    public val to: String,
    public val configuration: String,
)

@Serializable
public data class GoldCatalogLibrary(public val module: String, public val version: String? = null)

@Serializable
public data class GoldCatalogPlugin(public val id: String, public val version: String? = null)

@Serializable
public data class GoldCatalog(
    public val libraries: Map<String, GoldCatalogLibrary> = emptyMap(),
    public val plugins: Map<String, GoldCatalogPlugin> = emptyMap(),
    public val bundles: Map<String, List<String>> = emptyMap(),
)

@Serializable
public data class GoldConventionPlugin(
    public val id: String,
    public val file: String,
    public val applies: List<String> = emptyList(),
    public val catalogLibraries: List<String> = emptyList(),
    public val appliedTo: List<String> = emptyList(),
)

/** The build model of the reference workspace as an exact Gradle run reports it. */
@Serializable
public data class GoldBuildModel(
    public val workspace: String,
    public val gradleVersion: String,
    public val includedBuilds: List<String> = emptyList(),
    public val catalog: GoldCatalog = GoldCatalog(),
    public val conventionPlugins: List<GoldConventionPlugin> = emptyList(),
    public val modules: List<GoldModule> = emptyList(),
    public val projectDependencies: List<GoldProjectDependency> = emptyList(),
)

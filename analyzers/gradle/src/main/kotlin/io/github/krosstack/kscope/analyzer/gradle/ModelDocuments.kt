package io.github.krosstack.kscope.analyzer.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val SUPPORTED_SCHEMA = 1

@Serializable
internal data class BuildDocument(
    val name: String,
    val rootDir: String,
    val included: Boolean = false,
    val gradleVersion: String? = null,
)

@Serializable
internal data class KotlinCompilationDocument(
    val name: String,
    val allSourceSets: List<String> = emptyList(),
    val defaultSourceSet: String? = null,
)

@Serializable
internal data class KotlinTargetDocument(
    val name: String,
    val platformType: String,
    val compilations: List<KotlinCompilationDocument> = emptyList(),
)

@Serializable
internal data class KotlinSourceSetDocument(
    val name: String,
    val kotlinDirs: List<String> = emptyList(),
    val resourceDirs: List<String> = emptyList(),
    val dependsOn: List<String> = emptyList(),
)

@Serializable
internal data class KotlinDocument(
    val sourceSets: List<KotlinSourceSetDocument> = emptyList(),
    val targets: List<KotlinTargetDocument> = emptyList(),
)

@Serializable
internal data class AndroidSourceSetDocument(
    val name: String,
    val dirs: Map<String, List<String>> = emptyMap(),
    val manifest: String? = null,
)

@Serializable
internal data class AndroidVariantSourceSets(
    val main: List<String> = emptyList(),
    val unitTest: List<String> = emptyList(),
    val androidTest: List<String> = emptyList(),
)

@Serializable
internal data class AndroidVariantDocument(
    val name: String,
    val buildType: String? = null,
    val flavors: List<String> = emptyList(),
    val sourceSets: AndroidVariantSourceSets = AndroidVariantSourceSets(),
    val buildConfigFields: Map<String, String?> = emptyMap(),
)

@Serializable
internal data class AndroidDocument(
    val kind: String,
    val namespace: String? = null,
    val compileSdk: String? = null,
    val buildFeatures: List<String> = emptyList(),
    val flavorDimensions: List<String> = emptyList(),
    val sourceSets: List<AndroidSourceSetDocument> = emptyList(),
    val variants: List<AndroidVariantDocument> = emptyList(),
) {
    val isDsl: Boolean get() = kind == "dsl"
}

@Serializable
internal data class JavaSourceSetDocument(
    val name: String,
    val javaDirs: List<String> = emptyList(),
    val resourceDirs: List<String> = emptyList(),
)

@Serializable
internal data class ProjectDependencyDocument(val configuration: String, val path: String)

@Serializable
internal data class ExternalDependencyDocument(
    val configuration: String,
    val coordinates: String,
    val version: String? = null,
)

/** A file of a file dependency, at its absolute [path]. */
@Serializable
internal data class FileDependencyDocument(val configuration: String, val path: String)

/** A file tree of a file dependency: its [dir] at an absolute path and the patterns that select its files. */
@Serializable
internal data class TreeDependencyDocument(
    val configuration: String,
    val dir: String,
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
)

@Serializable
internal data class DependenciesDocument(
    val projects: List<ProjectDependencyDocument> = emptyList(),
    val external: List<ExternalDependencyDocument> = emptyList(),
    val files: List<FileDependencyDocument> = emptyList(),
    val trees: List<TreeDependencyDocument> = emptyList(),
)

@Serializable
internal data class SqldelightDatabaseDocument(val name: String, val packageName: String? = null)

@Serializable
internal data class ComposeResourcesDocument(
    val packageOfResClass: String? = null,
    val publicResClass: Boolean? = null,
    val group: String? = null,
)

@Serializable
internal data class GeneratorsDocument(
    val sqldelightDatabases: List<SqldelightDatabaseDocument> = emptyList(),
    val wireKotlin: Boolean? = null,
    val kspProcessors: List<String> = emptyList(),
    val composeResources: ComposeResourcesDocument? = null,
)

@Serializable
internal data class ClasspathEntryDocument(
    val kind: String,
    val name: String,
    val sourceSets: List<String> = emptyList(),
    val files: List<Int> = emptyList(),
)

@Serializable
internal data class ClasspathsDocument(
    val files: List<String> = emptyList(),
    val entries: List<ClasspathEntryDocument> = emptyList(),
)

@Serializable
internal data class ProjectDocument(
    val schema: Int,
    val path: String,
    val dir: String,
    val buildDir: String,
    val build: BuildDocument,
    val plugins: List<String> = emptyList(),
    val kotlin: KotlinDocument? = null,
    val android: AndroidDocument? = null,
    val java: List<JavaSourceSetDocument>? = null,
    val dependencies: DependenciesDocument = DependenciesDocument(),
    val archives: List<String> = emptyList(),
    val generators: GeneratorsDocument = GeneratorsDocument(),
    val publication: PublicationDocument? = null,
    val classpaths: ClasspathsDocument = ClasspathsDocument(),
    val errors: Map<String, List<String>> = emptyMap(),
)

internal object ModelDocuments {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** One fetch: an array of project documents. */
    fun parse(text: String): List<ProjectDocument> = json.decodeFromString<List<ProjectDocument>>(text)

    /** All fetches of a workspace: an array of arrays of project documents, each project once. */
    fun parseFetches(text: String): List<ProjectDocument> = json.decodeFromString<List<List<ProjectDocument>>>(text)
        .flatten()
        .distinctBy { it.build.rootDir to it.path }
}

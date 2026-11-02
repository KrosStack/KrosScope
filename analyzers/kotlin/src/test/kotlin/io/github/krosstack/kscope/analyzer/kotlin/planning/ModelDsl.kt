package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.VariantId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule

private const val REPOSITORY = "repo"
private const val BUILD_ROOT = "repo//"

internal fun sourceSetId(modulePath: String, name: String) = SourceSetId("$BUILD_ROOT$modulePath@$name")

/** The name the planner gives the analysis module of a single source set. */
internal fun moduleName(modulePath: String, sourceSet: String): String = sourceSetId(modulePath, sourceSet).value

/** The name the planner gives the analysis module that groups the main source sets of a default variant. */
internal fun groupName(modulePath: String, variant: String): String = "$BUILD_ROOT$modulePath#variant/$variant"

private fun roleOf(name: String): SourceSetRole = when {
    name.startsWith("androidTest") || name.endsWith("InstrumentedTest") -> SourceSetRole.INSTRUMENTED_TEST
    name.startsWith("testFixtures") -> SourceSetRole.TEST_FIXTURES
    name == "test" || name.endsWith("Test") || name.startsWith("test") -> SourceSetRole.UNIT_TEST
    else -> SourceSetRole.MAIN
}

internal fun model(block: ModelDsl.() -> Unit): BuildModel = ModelDsl().apply(block).build()

internal class ModelDsl {
    private val modules = mutableListOf<Module>()
    private val targets = mutableListOf<Target>()
    private val variants = mutableListOf<Variant>()
    private val sourceSets = mutableListOf<SourceSet>()
    private val dependencies = mutableListOf<Relationship>()

    fun module(path: String, block: ModuleDsl.() -> Unit) {
        val dsl = ModuleDsl(path).apply(block)
        modules += dsl.module()
        targets += dsl.targets
        variants += dsl.variants
        sourceSets += dsl.sourceSets
    }

    /** [scope] names a source set of the depending module; [configuration] is the Gradle configuration. */
    fun dependency(
        from: String,
        to: String,
        scope: String? = null,
        configuration: String = "implementation",
        certainty: Certainty = Certainty.EXACT,
    ) {
        dependencies += edge(from, NodeRef.OfModule(ModuleId(BUILD_ROOT + to)), scope, configuration, certainty)
    }

    fun external(from: String, coordinate: String) {
        dependencies += edge(from, NodeRef.OfExternal(coordinate), null, "implementation", Certainty.EXACT)
    }

    fun dependencyFromSourceSet(from: String, sourceSet: String, to: String) {
        val source = NodeRef.OfSourceSet(sourceSetId(from, sourceSet))
        dependencies += Relationship(
            from = source,
            kind = RelationKind.DEPENDS_ON,
            subKind = null,
            resolution = Resolution.Resolved(NodeRef.OfModule(ModuleId(BUILD_ROOT + to)), Certainty.EXACT),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(),
        )
    }

    fun build() = BuildModel(
        buildRoots = listOf(
            BuildRoot(BuildRootId(BUILD_ROOT), RepositoryId(REPOSITORY), RepoPath(""), BuildSystem.GRADLE),
        ),
        modules = modules,
        targets = targets,
        variants = variants,
        sourceSets = sourceSets,
        dependencies = dependencies,
    )

    private fun edge(from: String, to: NodeRef, scope: String?, configuration: String, certainty: Certainty) =
        Relationship(
            from = NodeRef.OfModule(ModuleId(BUILD_ROOT + from)),
            kind = RelationKind.DEPENDS_ON,
            subKind = null,
            resolution = Resolution.Resolved(to, certainty),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(sourceSet = scope?.let { sourceSetId(from, it) }),
            attributes = mapOf("configuration" to configuration),
        )
}

internal class ModuleDsl(private val path: String) {
    private val moduleId = ModuleId(BUILD_ROOT + path)
    val targets = mutableListOf<Target>()
    val variants = mutableListOf<Variant>()
    val sourceSets = mutableListOf<SourceSet>()

    fun module() = Module(
        id = moduleId,
        buildRoot = BuildRootId(BUILD_ROOT),
        path = path,
        directory = RepoPath(path.trim(':').replace(':', '/')),
        plugins = emptyList(),
        traits = emptySet(),
        androidNamespace = null,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    fun target(name: String, platform: Platform) {
        targets += Target(TargetId("${moduleId.value}#target/$name"), moduleId, name, platform)
    }

    fun variant(name: String, buildType: String?, vararg flavors: String) {
        variants += Variant(variantId(name), moduleId, name, buildType, flavors.toList())
    }

    fun sourceSet(
        name: String,
        dependsOn: List<String> = emptyList(),
        targets: List<String> = emptyList(),
        variants: List<String> = emptyList(),
    ) {
        sourceSets += SourceSet(
            id = sourceSetId(path, name),
            module = moduleId,
            name = name,
            role = roleOf(name),
            dependsOn = dependsOn.map { sourceSetId(path, it) },
            roots = emptyList(),
            targets = targets.map { TargetId("${moduleId.value}#target/$it") },
            variants = variants.map(::variantId),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
        )
    }

    private fun variantId(name: String) = VariantId("${moduleId.value}#variant/$name")
}

/** One file per source set unless the source set is listed in [without]. */
internal fun BuildModel.filesOf(without: Set<SourceSetId> = emptySet()): Map<SourceSetId, List<WorkerFile>> =
    sourceSets.filter { it.id !in without }.associate { it.id to listOf(fileIn(it)) }

private fun fileIn(sourceSet: SourceSet): WorkerFile {
    val path = "${sourceSet.module.value.removePrefix(BUILD_ROOT).trim(':').replace(':', '/')}/${sourceSet.name}/A.kt"
    return WorkerFile(
        ArtifactRef(RepositoryId(REPOSITORY), RepoPath(path)),
        "/workspace/$path",
        ContentHash("hash-$path"),
    )
}

internal fun BuildModel.plan(
    requested: Collection<SourceSetId>,
    without: Set<SourceSetId> = emptySet(),
    synthetic: List<SyntheticSource> = emptyList(),
    classpaths: Map<SourceSetId, List<String>> = emptyMap(),
): AnalysisPlan = AnalysisModulePlanner().plan(
    PlanInput(this, filesOf(without), synthetic, classpaths, requested.toSet()),
)

internal fun BuildModel.planAll(): AnalysisPlan = plan(sourceSets.map { it.id })

internal fun AnalysisPlan.module(name: String): WorkerModule =
    modules.singleOrNull { it.name == name } ?: error("No module $name among ${modules.map { it.name }}")

package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path

private const val ROOT_KIND = "compose.resources"
private val repository = RepositoryId("repo")
private val unknownType = ArtifactType("unknown", ArtifactCategory.OTHER)

/** What a test says about the module that holds the resources. */
internal class ModuleSpec(
    val path: String = ":ui:shared",
    val plugins: List<String> = listOf("org.jetbrains.compose"),
    val attributes: Map<String, String> = emptyMap(),
    val sourceSets: List<String> = listOf("commonMain", "androidMain", "desktopMain"),
) {
    val directory: String get() = path.removePrefix(":").replace(':', '/').ifEmpty { "root" }
    val moduleId: ModuleId get() = ModuleId("repo//$path")
}

/** A workspace of one module in memory: its resource files, classified by the detectors, and its build model. */
internal class ComposeTree(private val spec: ModuleSpec, private val contents: Map<String, String>) {
    val module = Module(
        id = spec.moduleId,
        buildRoot = BuildRootId("repo//"),
        path = spec.path,
        directory = RepoPath(spec.directory),
        plugins = spec.plugins,
        traits = setOf(ModuleTrait.KOTLIN_MULTIPLATFORM),
        androidNamespace = null,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
        attributes = spec.attributes,
    )
    val model = BuildModel(
        buildRoots = listOf(BuildRoot(BuildRootId("repo//"), repository, RepoPath(""), BuildSystem.GRADLE)),
        modules = listOf(module),
        targets = listOf(Target(TargetId("repo//${spec.path}#target/jvm"), spec.moduleId, "jvm", Platform.JVM)),
        variants = emptyList(),
        sourceSets = spec.sourceSets.map(::sourceSetOf),
        dependencies = emptyList(),
    )
    val artifacts: List<Artifact> = contents.keys.sorted().map(::artifactOf)
    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = contents.getValue(ref.path.value).toByteArray()

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    fun sourceSetId(name: String) = SourceSetId("repo//${spec.path}@$name")

    private fun sourceSetOf(name: String) = SourceSet(
        id = sourceSetId(name),
        module = spec.moduleId,
        name = name,
        role = if (name.endsWith("Test")) SourceSetRole.UNIT_TEST else SourceSetRole.MAIN,
        dependsOn = if (name == "commonMain" || name == "main") emptyList() else listOf(sourceSetId("commonMain")),
        roots = listOf(
            SourceRoot(RepoPath("${spec.directory}/src/$name/composeResources"), SourceRootKind(ROOT_KIND), false),
        ),
        targets = emptyList(),
        variants = emptyList(),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    private fun artifactOf(path: String): Artifact {
        val bytes = contents.getValue(path).toByteArray()
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
        return Artifact(
            ref = ArtifactRef(repository, RepoPath(path)),
            type = ComposeCapabilities.detectors().firstNotNullOfOrNull { it.detect(candidate) } ?: unknownType,
            contentHash = ContentHash("hash-of-$path"),
            sizeBytes = bytes.size.toLong(),
            lineCount = contents.getValue(path).count { it == '\n' },
            sourceSet = model.sourceSets.firstOrNull { set ->
                set.roots.any { path.startsWith("${it.directory.value}/") }
            }?.id,
            generated = null,
        )
    }

    fun artifact(path: String): Artifact = artifacts.single { it.ref.path.value == path }

    fun produce(producer: FactProducer, path: String): List<Fact> {
        val artifact = artifact(path)
        val sourceSet = model.sourceSets.firstOrNull { it.id == artifact.sourceSet }
        val owner = OwnershipUnit(OwnershipKind.ARTIFACT, path)
        val request = request(listOf(AnalysisUnit(owner, listOf(artifact), sourceSet, module)))
        val batches = mutableListOf<FactBatch>()
        producer.produce(request) { batches += it }
        return batches.flatMap { it.facts }
    }

    /** Runs a synthesizer of module units: the unit holds the artifacts that the synthesizer consumes. */
    fun synthesize(synthesizer: SurfaceSynthesizer): Synthesis {
        val consumed = artifacts.filter { it.type in synthesizer.descriptor.consumes }
        val owner = OwnershipUnit(OwnershipKind.MODULE, spec.moduleId.value)
        val units = if (consumed.isEmpty()) emptyList() else listOf(AnalysisUnit(owner, consumed, null, module))
        val batches = mutableListOf<FactBatch>()
        val sources = synthesizer.synthesize(request(units)) { batches += it }
        return Synthesis(batches.flatMap { it.facts }, sources)
    }

    private fun request(units: List<AnalysisUnit>) =
        ProduceRequest(units, model, files, emptyList(), emptyMap(), artifacts)
}

internal class Synthesis(val facts: List<Fact>, val sources: List<SyntheticSource>)

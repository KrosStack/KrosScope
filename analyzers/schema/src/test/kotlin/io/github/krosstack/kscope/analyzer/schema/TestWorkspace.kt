package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * A module of a test workspace: where it is, which source sets it has, what its build model says about it, and the
 * platforms of its targets.
 */
internal class ModuleSpec(
    val path: String,
    val sourceSets: List<String> = listOf("main"),
    val attributes: Map<String, String> = emptyMap(),
    val roots: List<String> = emptyList(),
    val platforms: List<Platform> = emptyList(),
)

/**
 * The files of a workspace kept in memory, classified by the detectors and assigned to the source sets of a build model
 * made from module specifications. A source set owns the directory `src/<name>` of its module.
 */
internal class TestWorkspace(files: Map<String, String>, modules: List<ModuleSpec>) {
    val model: BuildModel = modelOf(modules)
    private val contents: Map<String, ByteArray> = files.mapValues { it.value.toByteArray() }
    val artifacts: List<Artifact> = contents.keys.sorted().map(::artifactOf)

    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = contents.getValue(ref.path.value)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    fun artifact(path: String): Artifact = artifacts.single { it.ref.path.value == path }

    fun request(units: List<AnalysisUnit>) = ProduceRequest(units, model, files, emptyList(), emptyMap(), artifacts)

    fun produce(producer: FactProducer, only: (Artifact) -> Boolean = { true }): List<FactBatch> {
        val consumed = artifacts.filter { it.type in producer.descriptor.consumes }.filter(only)
        val batches = mutableListOf<FactBatch>()
        producer.produce(request(consumed.map(::artifactUnit))) { batches += it }
        return batches
    }

    fun produceBySourceSet(producer: FactProducer): List<FactBatch> {
        val consumed = artifacts.filter { it.type in producer.descriptor.consumes }
        val units = consumed.groupBy { it.sourceSet }.map { (id, members) -> sourceSetUnit(id, members) }
        val batches = mutableListOf<FactBatch>()
        producer.produce(request(units)) { batches += it }
        return batches
    }

    fun synthesize(synthesizer: SurfaceSynthesizer): Pair<List<FactBatch>, List<SyntheticSource>> {
        val consumed = artifacts.filter { it.type in synthesizer.descriptor.consumes }
        val units = model.modules.mapNotNull { module -> moduleUnit(module, consumed) }
        val batches = mutableListOf<FactBatch>()
        val sources = synthesizer.synthesize(request(units)) { batches += it }
        return batches to sources
    }

    private fun artifactUnit(artifact: Artifact): AnalysisUnit {
        val sourceSet = model.sourceSets.firstOrNull { it.id == artifact.sourceSet }
        val module = model.modules.firstOrNull { it.id == sourceSet?.module }
        return AnalysisUnit(
            OwnershipUnit(OwnershipKind.ARTIFACT, artifact.ref.path.value),
            listOf(artifact),
            sourceSet,
            module,
        )
    }

    private fun sourceSetUnit(id: SourceSetId?, members: List<Artifact>): AnalysisUnit {
        val sourceSet = model.sourceSets.firstOrNull { it.id == id }
        val module = model.modules.firstOrNull { it.id == sourceSet?.module }
        return AnalysisUnit(OwnershipUnit(OwnershipKind.SOURCE_SET, id?.value ?: "none"), members, sourceSet, module)
    }

    private fun moduleUnit(module: Module, consumed: List<Artifact>): AnalysisUnit? {
        val members = consumed.filter { artifact ->
            val prefix = module.directory.value
            prefix.isEmpty() || artifact.ref.path.value.startsWith("$prefix/")
        }
        val owner = OwnershipUnit(OwnershipKind.MODULE, module.id.value)
        return members.takeIf { it.isNotEmpty() }?.let { AnalysisUnit(owner, it, null, module) }
    }

    private fun artifactOf(path: String): Artifact {
        val bytes = contents.getValue(path)
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
        return Artifact(
            ref = ArtifactRef(TestRepository.id, RepoPath(path)),
            type = SchemaCapabilities.detectors().firstNotNullOfOrNull { it.detect(candidate) } ?: otherType(path),
            contentHash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
            sizeBytes = bytes.size.toLong(),
            lineCount = bytes.count { it == '\n'.code.toByte() },
            sourceSet = model.sourceSets.firstOrNull { set ->
                set.roots.any { path.startsWith("${it.directory.value}/") }
            }?.id,
            generated = null,
        )
    }

    private companion object {
        val unknownType = ArtifactType("unknown", ArtifactCategory.OTHER)

        fun otherType(path: String): ArtifactType =
            if (path.endsWith(".kt")) SchemaArtifactTypes.kotlinSource else unknownType

        fun modelOf(specs: List<ModuleSpec>) = BuildModel(
            buildRoots = listOf(
                BuildRoot(TestRepository.buildRoot, TestRepository.id, RepoPath(""), BuildSystem.GRADLE),
            ),
            modules = specs.map(::moduleOf),
            targets = emptyList(),
            variants = emptyList(),
            sourceSets = specs.flatMap { spec -> spec.sourceSets.map { sourceSetOf(spec, it) } },
            dependencies = emptyList(),
        )

        fun moduleOf(spec: ModuleSpec) = Module(
            id = TestRepository.moduleId(spec.path),
            buildRoot = TestRepository.buildRoot,
            path = spec.path,
            directory = RepoPath(spec.path.removePrefix(":").replace(':', '/')),
            plugins = emptyList(),
            traits = emptySet(),
            androidNamespace = null,
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
            attributes = spec.attributes,
        )

        fun sourceSetOf(spec: ModuleSpec, name: String): SourceSet {
            val directory = spec.path.removePrefix(":").replace(':', '/')
            return SourceSet(
                id = TestRepository.sourceSetId(spec.path, name),
                module = TestRepository.moduleId(spec.path),
                name = name,
                role = SourceSetRole.MAIN,
                dependsOn = emptyList(),
                roots = listOf(SourceRoot(RepoPath("$directory/src/$name"), SourceRootKind("kotlin"), false)),
                targets = emptyList(),
                variants = emptyList(),
                evidenceClass = EvidenceClass.BUILD_MODEL,
                certainty = Certainty.EXACT,
            )
        }
    }
}

internal fun List<FactBatch>.facts(): List<Fact> = flatMap { it.facts }

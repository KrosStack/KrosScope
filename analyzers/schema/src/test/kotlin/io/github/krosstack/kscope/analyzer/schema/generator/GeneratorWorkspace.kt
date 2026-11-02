package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.SchemaCapabilities
import io.github.krosstack.kscope.analyzer.schema.web.WebBundleCapabilities
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
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * A repository of a test workspace: its files, its modules, and where its checkout lies below the directory the
 * workspace is made in, which is a directory of its own name unless [location] says otherwise. A module that has
 * `roots` in its specification owns those directories of the repository, as source set roots, in place of `src/<name>`.
 */
internal class RepositorySpec(
    val name: String,
    val files: Map<String, String>,
    val modules: List<ModuleSpec> = listOf(ModuleSpec(":")),
    val location: String = name,
)

/** What a synthesizer made of a workspace: its batches, and the synthetic sources it returned. */
internal class Synthesis(val batches: List<FactBatch>, val sources: List<SyntheticSource>) {
    val facts: List<Fact> get() = batches.flatMap { it.facts }
}

/**
 * Files of several repositories kept in memory, with checkouts in directories of a temporary directory that the
 * locations of the files lie in. Each repository is one build root with its modules; the files are classified by the
 * detectors of the schema analyzer and assigned to the source sets of their modules.
 */
internal class GeneratorWorkspace(private val repositories: List<RepositorySpec>) : AutoCloseable {
    private val parent: Path = Files.createTempDirectory("kscope-generators").toRealPath()
    private val contents: Map<ArtifactRef, ByteArray> = repositories.flatMap { repository ->
        repository.files.map { (path, text) ->
            ArtifactRef(idOf(repository.name), RepoPath(path)) to text.toByteArray()
        }
    }.toMap()

    val roots: Map<String, Path> = repositories.associate { it.name to parent.resolve(it.location) }
    val model: BuildModel = modelOf()
    val artifacts: List<Artifact> = contents.keys.sortedWith(compareBy({ it.repository.value }, { it.path.value }))
        .map(::artifactOf)

    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = contents.getValue(ref)

        override fun absolutePath(ref: ArtifactRef): Path = roots.getValue(ref.repository.value).resolve(ref.path.value)
    }

    fun artifact(repository: String, path: String): Artifact =
        artifacts.single { it.ref.repository == idOf(repository) && it.ref.path.value == path }

    fun module(repository: String, path: String): Module =
        model.modules.single { it.id == moduleIdOf(repository, path) }

    fun request(consumes: Set<ArtifactType>): ProduceRequest {
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        val unit = AnalysisUnit(owner, artifacts.filter { it.type in consumes }, null, null)
        return ProduceRequest(listOf(unit), model, files, emptyList(), emptyMap(), artifacts)
    }

    fun synthesize(): Synthesis {
        val synthesizer = GeneratorInputSynthesizer()
        val batches = mutableListOf<FactBatch>()
        val sources = synthesizer.synthesize(request(synthesizer.descriptor.consumes)) { batches += it }
        return Synthesis(batches, sources)
    }

    fun produce(producer: FactProducer, artifact: Artifact): List<Fact> {
        val owner = OwnershipUnit(OwnershipKind.ARTIFACT, artifact.ref.path.value)
        val batches = mutableListOf<FactBatch>()
        producer.produce(request(emptySet()).copy(units = listOf(AnalysisUnit(owner, listOf(artifact), null, null)))) {
            batches += it
        }
        return batches.flatMap { it.facts }
    }

    override fun close() {
        parent.toFile().deleteRecursively()
    }

    private fun modelOf(): BuildModel {
        val modules = repositories.flatMap { repository -> repository.modules.map { moduleOf(repository.name, it) } }
        return BuildModel(
            buildRoots = repositories.map {
                BuildRoot(BuildRootId("${it.name}//"), idOf(it.name), RepoPath(""), BuildSystem.GRADLE)
            },
            modules = modules,
            targets = repositories.flatMap { repository ->
                repository.modules.flatMap { spec -> spec.platforms.map { targetOf(repository.name, spec, it) } }
            },
            variants = emptyList(),
            sourceSets = repositories.flatMap { repository ->
                repository.modules.flatMap { spec -> spec.sourceSets.map { sourceSetOf(repository.name, spec, it) } }
            },
            dependencies = emptyList(),
        )
    }

    private fun moduleOf(repository: String, spec: ModuleSpec) = Module(
        id = moduleIdOf(repository, spec.path),
        buildRoot = BuildRootId("$repository//"),
        path = spec.path,
        directory = RepoPath(directoryOf(spec.path)),
        plugins = emptyList(),
        traits = emptySet(),
        androidNamespace = null,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
        attributes = spec.attributes,
    )

    private fun targetOf(repository: String, spec: ModuleSpec, platform: Platform) = Target(
        id = TargetId("$repository//${spec.path}@${platform.name.lowercase()}"),
        module = moduleIdOf(repository, spec.path),
        name = platform.name.lowercase(),
        platform = platform,
    )

    private fun sourceSetOf(repository: String, spec: ModuleSpec, name: String): SourceSet {
        val directory = directoryOf(spec.path)
        val conventional = listOf(directory, "src", name).filter { it.isNotEmpty() }.joinToString("/")
        val roots = spec.roots.ifEmpty { listOf(conventional) }
        return SourceSet(
            id = SourceSetId("$repository//${spec.path}@$name"),
            module = moduleIdOf(repository, spec.path),
            name = name,
            role = SourceSetRole.MAIN,
            dependsOn = emptyList(),
            roots = roots.map { SourceRoot(RepoPath(it), SourceRootKind("kotlin"), false) },
            targets = emptyList(),
            variants = emptyList(),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
        )
    }

    private fun artifactOf(ref: ArtifactRef): Artifact {
        val bytes = contents.getValue(ref)
        return Artifact(
            ref = ref,
            type = typeOf(ref.path.value, bytes),
            contentHash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
            sizeBytes = bytes.size.toLong(),
            lineCount = bytes.count { it == '\n'.code.toByte() },
            sourceSet = model.sourceSets.filter { set -> set.module.value.startsWith("${ref.repository.value}//") }
                .flatMap { set -> set.roots.map { set to it.directory.value } }
                .filter { (_, root) -> root.isEmpty() || ref.path.value.startsWith("$root/") }
                .maxByOrNull { (_, root) -> root.length }?.first?.id,
            generated = null,
        )
    }
}

private val buildScriptNames = setOf("build.gradle.kts", "build.gradle")

private val detectors = SchemaCapabilities.detectors() + WebBundleCapabilities.detectors()

private fun idOf(repository: String) = RepositoryId(repository)

private fun moduleIdOf(repository: String, path: String) = ModuleId("$repository//$path")

private fun directoryOf(path: String): String = path.removePrefix(":").replace(':', '/')

private fun typeOf(path: String, bytes: ByteArray): ArtifactType {
    val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
    return detectors.firstNotNullOfOrNull { it.detect(candidate) } ?: when {
        path.substringAfterLast('/') in buildScriptNames -> SchemaArtifactTypes.buildScript
        path.endsWith(".kt") -> SchemaArtifactTypes.kotlinSource
        else -> ArtifactType("unknown", ArtifactCategory.OTHER)
    }
}

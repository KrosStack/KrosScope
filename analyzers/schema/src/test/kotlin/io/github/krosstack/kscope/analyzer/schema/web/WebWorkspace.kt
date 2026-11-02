package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.SchemaCapabilities
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
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.OutputRoot
import io.github.krosstack.kscope.spi.OutputRootRequest
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

private val repository = RepositoryId("repo")
private val buildRoot = BuildRootId("repo//")
private val moduleId = ModuleId("repo//:")
private val unknownType = ArtifactType("unknown", ArtifactCategory.OTHER)

/**
 * The files of a repository kept in memory, classified by the web detectors together with the schema detectors, as the
 * registry of a scan orders them. The repository has one module in the directory [moduleDirectory], whose source
 * sets are rooted at the directories in [sourceSetRoots], and the files outside them still belong to the module that
 * holds them. Every file below one of the [outputDirectories] is
 * labelled as the output of a bundler, as discovery does, and the files of the other directories are hand-written.
 */
internal class WebWorkspace(
    files: Map<String, String>,
    private val outputDirectories: List<String> = emptyList(),
    private val sourceSetRoots: Map<String, String> = mapOf("main" to ""),
    private val moduleDirectory: String = "",
) {
    private val contents: Map<String, ByteArray> = files.mapValues { it.value.toByteArray() }
    private val detectors = CapabilityRegistry(
        detectors = WebBundleCapabilities.detectors() + SchemaCapabilities.detectors(),
    ).detectors

    val model: BuildModel = modelOf()
    val artifacts: List<Artifact> = contents.keys.sorted().map(::artifactOf)

    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = contents.getValue(ref.path.value)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    fun artifact(path: String): Artifact = artifacts.single { it.ref.path.value == path }

    fun request(units: List<AnalysisUnit>) = ProduceRequest(units, model, files, emptyList(), emptyMap(), artifacts)

    /** The unit of the repository, with every web file, as the planner makes it. */
    fun units(): List<AnalysisUnit> {
        val consumed = artifacts.filter { it.type in WebArtifactTypes.all }
        val owner = OwnershipUnit(OwnershipKind.REPOSITORY, repository.value)
        return if (consumed.isEmpty()) emptyList() else listOf(AnalysisUnit(owner, consumed, null, null))
    }

    fun produce(): List<FactBatch> {
        val batches = mutableListOf<FactBatch>()
        WebBundleProducer().produce(request(units())) { batches += it }
        return batches
    }

    /** What the detectors name as output roots for the files of this workspace. */
    fun outputRoots(): List<OutputRoot> {
        val request = OutputRootRequest(artifacts, files)
        return WebBundleCapabilities.detectors().flatMap { it.outputRoots(request) }
    }

    private fun artifactOf(path: String): Artifact {
        val bytes = contents.getValue(path)
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
        val isOutput = outputDirectories.any { path.startsWith("$it/") }
        return Artifact(
            ref = ArtifactRef(repository, RepoPath(path)),
            type = detectors.firstNotNullOfOrNull { it.detect(candidate) } ?: unknownType,
            contentHash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
            sizeBytes = bytes.size.toLong(),
            lineCount = bytes.count { it == '\n'.code.toByte() },
            sourceSet = sourceSetOf(path)?.id,
            generated = GeneratedInfo(OutputRoot(RepoPath("out"), "vite").label, null).takeIf { isOutput },
        )
    }

    private fun sourceSetOf(path: String): SourceSet? = model.sourceSets
        .filter { set -> set.roots.single().directory.value.let { it.isEmpty() || path.startsWith("$it/") } }
        .maxByOrNull { it.roots.single().directory.value.length }

    private fun modelOf(): BuildModel {
        val module = Module(
            id = moduleId,
            buildRoot = buildRoot,
            path = ":",
            directory = RepoPath(moduleDirectory),
            plugins = emptyList(),
            traits = emptySet(),
            androidNamespace = null,
            evidenceClass = EvidenceClass.CONVENTION,
            certainty = Certainty.HEURISTIC,
        )
        return BuildModel(
            buildRoots = listOf(BuildRoot(buildRoot, repository, RepoPath(""), BuildSystem.NONE)),
            modules = listOf(module),
            targets = emptyList(),
            variants = emptyList(),
            sourceSets = sourceSetRoots.map { (name, root) -> sourceSetOf(name, root) },
            dependencies = emptyList(),
        )
    }

    private fun sourceSetOf(name: String, root: String) = SourceSet(
        id = SourceSetId("${moduleId.value}@$name"),
        module = moduleId,
        name = name,
        role = SourceSetRole.MAIN,
        dependsOn = emptyList(),
        roots = listOf(SourceRoot(RepoPath(root), SourceRootKind("repository"), generated = false)),
        targets = emptyList(),
        variants = emptyList(),
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.HEURISTIC,
    )
}

package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

private val unknownType = ArtifactType("unknown", ArtifactCategory.OTHER)

/** The files of a checked-in tree, classified by the detectors and assigned to the source sets of the model. */
internal class SampleTree(
    name: String,
    val model: BuildModel = DroidModel.standard(),
    overrides: Map<String, String> = emptyMap(),
) {
    val contents: Map<String, ByteArray> = readTree(name) + overrides.mapValues { it.value.toByteArray() } -
        overrides.filterValues { it.isEmpty() }.keys
    val artifacts: List<Artifact> = contents.keys.sorted().map(::artifactOf)
    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = contents.getValue(ref.path.value)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    fun artifact(path: String): Artifact = artifacts.single { it.ref.path.value == path }

    fun unitOf(artifact: Artifact): AnalysisUnit {
        val sourceSet = model.sourceSets.firstOrNull { it.id == artifact.sourceSet }
        val module = model.modules.firstOrNull { it.id == sourceSet?.module }
        val owner = OwnershipUnit(OwnershipKind.ARTIFACT, artifact.ref.path.value)
        return AnalysisUnit(owner, listOf(artifact), sourceSet, module)
    }

    fun requestFor(units: List<AnalysisUnit>) = ProduceRequest(units, model, files, emptyList(), emptyMap(), artifacts)

    fun produce(producer: FactProducer, only: (Artifact) -> Boolean = { true }): List<FactBatch> {
        val wanted = artifacts.filter { it.type in producer.descriptor.consumes }.filter(only)
        return collect { producer.produce(requestFor(wanted.map(::unitOf)), it) }
    }

    fun synthesize(synthesizer: SurfaceSynthesizer): Pair<List<FactBatch>, List<SyntheticSource>> {
        val consumed = artifacts.filter { it.type in synthesizer.descriptor.consumes }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        val request = requestFor(listOf(AnalysisUnit(owner, consumed, null, null)))
        val batches = mutableListOf<FactBatch>()
        val sources = synthesizer.synthesize(request) { batches += it }
        return batches to sources
    }

    /** Runs a synthesizer whose units are modules: each module with consumed artifacts is one unit. */
    fun synthesizeModules(synthesizer: SurfaceSynthesizer): Pair<List<FactBatch>, List<SyntheticSource>> {
        val consumed = artifacts.filter { it.type in synthesizer.descriptor.consumes }
        val units = consumed.groupBy(::moduleOf).filterKeys { it != null }.map { (module, members) ->
            AnalysisUnit(OwnershipUnit(OwnershipKind.MODULE, checkNotNull(module).id.value), members, null, module)
        }
        val batches = mutableListOf<FactBatch>()
        val sources = synthesizer.synthesize(requestFor(units)) { batches += it }
        return batches to sources
    }

    private fun moduleOf(artifact: Artifact): Module? {
        val path = artifact.ref.path.value
        val containing = model.modules.filter { path.startsWith("${it.directory.value}/") }
        return containing.maxByOrNull { it.directory.value.length }
    }

    private fun collect(run: (FactSink) -> Unit): List<FactBatch> = mutableListOf<FactBatch>().also { batches ->
        run { batches += it }
    }

    private fun artifactOf(path: String): Artifact {
        val bytes = contents.getValue(path)
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
        return Artifact(
            ref = ArtifactRef(DroidModel.repository, RepoPath(path)),
            type = AndroidCapabilities.detectors().firstNotNullOfOrNull { it.detect(candidate) }
                ?: otherDetectors(candidate)
                ?: unknownType,
            contentHash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
            sizeBytes = bytes.size.toLong(),
            lineCount = bytes.count { it == '\n'.code.toByte() },
            sourceSet = model.sourceSets.firstOrNull { set ->
                set.roots.any { path == it.directory.value || path.startsWith("${it.directory.value}/") }
            }?.id,
            generated = null,
        )
    }

    private fun otherDetectors(candidate: ArtifactCandidate): ArtifactType? =
        when (candidate.path.value.substringAfterLast('/')) {
            "gradle.properties" -> AndroidArtifactTypes.gradleProperties
            "build.gradle", "build.gradle.kts" -> AndroidArtifactTypes.buildScript
            else -> null
        }

    private companion object {
        fun readTree(name: String): Map<String, ByteArray> {
            val root = Path.of(
                checkNotNull(SampleTree::class.java.getResource("/trees/$name")) { "No tree $name" }.toURI(),
            )
            return Files.walk(root).use { paths ->
                paths.filter(Files::isRegularFile).toList().associate { file ->
                    root.relativize(file).toString().replace('\\', '/') to Files.readAllBytes(file)
                }
            }
        }
    }
}

internal fun List<FactBatch>.facts(): List<Fact> = flatMap { it.facts }

package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.aidl.stub.AidlSurfaceSynthesizer
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

internal class Synthesis(val batches: List<FactBatch>, val sources: List<SyntheticSource>) {
    val facts: List<Fact> get() = batches.flatMap { it.facts }

    fun source(name: String): SyntheticSource = sources.single { it.virtualPath.endsWith("/$name.java") }
}

private val unknownType = ArtifactType("unknown", ArtifactCategory.OTHER)

/** The files of a test as artifacts of [model], classified by the detector and assigned to the source set of a root. */
internal class AidlWorkspace(contents: Map<String, String>, val model: BuildModel = AidlModel.standard()) {
    private val texts = contents.toSortedMap()
    val artifacts: List<Artifact> = texts.keys.map(::artifactOf)
    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = texts.getValue(ref.path.value).toByteArray()

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    fun artifact(path: String): Artifact = artifacts.single { it.ref.path.value == path }

    private fun unitOf(artifact: Artifact): AnalysisUnit {
        val sourceSet = model.sourceSets.firstOrNull { it.id == artifact.sourceSet }
        val module = model.modules.firstOrNull { it.id == sourceSet?.module }
        val owner = OwnershipUnit(OwnershipKind.ARTIFACT, artifact.ref.path.value)
        return AnalysisUnit(owner, listOf(artifact), sourceSet, module)
    }

    private fun request(only: (Artifact) -> Boolean) = ProduceRequest(
        units = artifacts.filter { it.type == AidlArtifactTypes.source }.filter(only).map(::unitOf),
        model = model,
        files = files,
        syntheticSources = emptyList(),
        classpaths = emptyMap(),
        artifacts = artifacts,
    )

    fun produce(only: (Artifact) -> Boolean = { true }): List<FactBatch> {
        val batches = mutableListOf<FactBatch>()
        AidlProducer().produce(request(only)) { batches += it }
        return batches
    }

    fun synthesize(only: (Artifact) -> Boolean = { true }): Synthesis {
        val batches = mutableListOf<FactBatch>()
        val sources = AidlSurfaceSynthesizer().synthesize(request(only)) { batches += it }
        return Synthesis(batches, sources)
    }

    private fun sourceSetOf(path: String) =
        model.sourceSets.firstOrNull { set -> set.roots.any { path.startsWith("${it.directory.value}/") } }?.id

    private fun artifactOf(path: String): Artifact {
        val bytes = texts.getValue(path).toByteArray()
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
        return Artifact(
            ref = ArtifactRef(AidlModel.repository, RepoPath(path)),
            type = AidlSourceDetector().detect(candidate) ?: unknownType,
            contentHash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
            sizeBytes = bytes.size.toLong(),
            lineCount = bytes.count { it == '\n'.code.toByte() },
            sourceSet = sourceSetOf(path),
            generated = null,
        )
    }
}

internal object AidlPaths {
    const val SERVICE = "svc/src/main/aidl/sample/tasktide/sync/service/ISyncService.aidl"
    const val CALLBACK = "svc/src/main/aidl/sample/tasktide/sync/service/ISyncCallback.aidl"
    const val STATUS = "svc/src/main/aidl/sample/tasktide/sync/service/SyncStatus.aidl"
    const val REPORT = "svc/src/main/aidl/sample/tasktide/sync/service/Report.aidl"
}

internal fun syncSample(): Map<String, String> = mapOf(
    AidlPaths.SERVICE to AidlSamples.syncService,
    AidlPaths.CALLBACK to AidlSamples.syncCallback,
    AidlPaths.STATUS to AidlSamples.syncStatus,
    AidlPaths.REPORT to AidlSamples.report,
)

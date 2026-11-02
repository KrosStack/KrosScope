package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.model
import io.github.krosstack.kscope.analyzer.kotlin.planning.sourceSetId
import io.github.krosstack.kscope.analyzer.kotlin.process.FakeWorkerHarness
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerTimeouts
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Three Jvm modules, `:app` depending on `:lib`, and producers that talk to the fake worker. */
internal class ProducerFixture : AutoCloseable {
    private val harness = FakeWorkerHarness()
    private val kotlinSource = ArtifactType("kotlin.source", ArtifactCategory.SOURCE)
    val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = ByteArray(0)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of("/workspace").resolve(ref.path.value)
    }

    val model: BuildModel = model {
        for (path in listOf(":app", ":lib", ":other")) {
            module(path) {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
        }
        dependency(from = ":app", to = ":lib")
    }
    val app: SourceSetId = sourceSetId(":app", "main")
    val lib: SourceSetId = sourceSetId(":lib", "main")
    val other: SourceSetId = sourceSetId(":other", "main")

    fun producerWith(mode: String, responseTimeout: Duration = 1.minutes): KotlinAnalyzerProducer {
        val timeouts = WorkerTimeouts(1.minutes, responseTimeout, 10.seconds)
        return KotlinAnalyzerProducer(harness.launcher(mode, timeouts)) {}
    }

    fun syntaxProducerWith(mode: String) = KotlinSyntaxProducer(harness.launcher(mode))

    fun artifactOf(sourceSet: SourceSetId): Artifact {
        val directory = sourceSet.value.removePrefix("repo//:").replace('@', '/')
        return Artifact(
            ref = ArtifactRef(RepositoryId("repo"), RepoPath("$directory/A.kt")),
            type = kotlinSource,
            contentHash = ContentHash("hash-${sourceSet.value}"),
            sizeBytes = 10,
            lineCount = 1,
            sourceSet = sourceSet,
            generated = null,
        )
    }

    fun unitOf(sourceSet: SourceSetId, buildModel: BuildModel = model): AnalysisUnit {
        val declared = buildModel.sourceSets.single { it.id == sourceSet }
        return AnalysisUnit(
            owner = OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.value),
            artifacts = listOf(artifactOf(sourceSet)),
            sourceSet = declared,
            module = buildModel.modules.single { it.id == declared.module },
        )
    }

    fun requestFor(
        requested: List<SourceSetId>,
        everything: List<SourceSetId> = listOf(app, lib, other),
        synthetic: List<SyntheticSource> = emptyList(),
        classpaths: Map<SourceSetId, List<String>> = emptyMap(),
        buildModel: BuildModel = model,
    ) = ProduceRequest(
        units = requested.map { unitOf(it, buildModel) },
        model = buildModel,
        files = files,
        syntheticSources = synthetic,
        classpaths = classpaths,
        artifacts = everything.map(::artifactOf),
    )

    fun run(producer: FactProducer, request: ProduceRequest): List<FactBatch> {
        val batches = mutableListOf<FactBatch>()
        producer.produce(request) { batches += it }
        return batches
    }

    override fun close() {
        harness.close()
    }
}

package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class WorkerFile(
    public val ref: ArtifactRef,
    public val absolutePath: String,
    public val contentHash: ContentHash,
)

/**
 * The files of one source set inside a [WorkerModule]. Facts are owned per source set: the worker answers with one
 * batch for each source set whose [emitFactsFor] is true and echoes [fingerprint] as the batch input fingerprint.
 * A source set with [emitFactsFor] false is context only, as for an unchanged dependency.
 */
@Serializable
public data class WorkerSourceSet(
    public val sourceSet: SourceSetId,
    public val module: ModuleId,
    public val files: List<WorkerFile>,
    public val syntheticSources: List<SyntheticSource>,
    public val emitFactsFor: Boolean,
    public val fingerprint: Fingerprint,
)

/**
 * A compilation-like analysis unit. Several source sets compile together when they belong to one Android variant
 * group; most modules hold exactly one. [name] is unique within the request and is what [dependsOn],
 * [dependencies] and [friends] refer to: [dependsOn] holds Kotlin Multiplatform refinement edges, [dependencies]
 * regular dependencies already flattened by the client, [friends] the modules whose internals are visible, as from a
 * test module to its main module. [classpath] lists absolute jar or directory paths and may be empty.
 */
@Serializable
public data class WorkerModule(
    public val name: String,
    public val platform: Platform,
    public val sourceSets: List<WorkerSourceSet>,
    public val dependsOn: List<String>,
    public val dependencies: List<String>,
    public val friends: List<String>,
    public val classpath: List<String>,
)

@Serializable
public sealed interface WorkerRequest {
    @Serializable
    @SerialName("handshake")
    public data class Handshake(public val protocolVersion: Int) : WorkerRequest

    @Serializable
    @SerialName("analyze")
    public data class Analyze(public val requestId: String, public val modules: List<WorkerModule>) : WorkerRequest

    /** Reads the declarations of the source sets from their syntax trees alone: no analysis session, no resolution. */
    @Serializable
    @SerialName("declare")
    public data class Declare(public val requestId: String, public val modules: List<WorkerModule>) : WorkerRequest

    @Serializable
    @SerialName("outline-scripts")
    public data class OutlineScripts(public val requestId: String, public val scripts: List<WorkerFile>) :
        WorkerRequest

    @Serializable
    @SerialName("digest-api")
    public data class DigestApi(public val requestId: String, public val sources: List<WorkerFile>) : WorkerRequest

    @Serializable
    @SerialName("shutdown")
    public data object Shutdown : WorkerRequest
}

@Serializable
public sealed interface WorkerResponse {
    @Serializable
    @SerialName("ready")
    public data class Ready(
        public val protocolVersion: Int,
        public val producer: ProducerRef,
        public val capabilities: Set<AnalyzerCapability>,
    ) : WorkerResponse

    /**
     * The facts of one batch, or a slice of them: [part] counts the messages of a batch from zero and [last] marks the
     * final one. See [BatchParts].
     */
    @Serializable
    @SerialName("batch")
    public data class Batch(
        public val requestId: String,
        public val batch: FactBatch,
        public val part: Int = 0,
        public val last: Boolean = true,
    ) : WorkerResponse

    @Serializable
    @SerialName("outline")
    public data class Outline(
        public val requestId: String,
        public val ref: ArtifactRef,
        public val outline: ScriptOutline,
    ) : WorkerResponse

    /** One answer for each source of a `digest-api` request; [digest] is null for a source that has no digest. */
    @Serializable
    @SerialName("api-digest")
    public data class ApiDigest(public val requestId: String, public val ref: ArtifactRef, public val digest: String?) :
        WorkerResponse

    @Serializable
    @SerialName("completed")
    public data class Completed(public val requestId: String) : WorkerResponse

    @Serializable
    @SerialName("failed")
    public data class Failed(
        public val requestId: String?,
        public val reason: ReasonCode,
        public val message: String,
    ) : WorkerResponse
}

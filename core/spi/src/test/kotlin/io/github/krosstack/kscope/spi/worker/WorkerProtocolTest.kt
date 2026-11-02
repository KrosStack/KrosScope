package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WorkerProtocolTest {
    private val ref = ArtifactRef(RepositoryId("r"), RepoPath("app/build.gradle.kts"))
    private val file = WorkerFile(ref, "/work/app/build.gradle.kts", ContentHash("ab".repeat(32)))
    private val sourceSet = SourceSetId("r//:app@main")

    private val workerSourceSet = WorkerSourceSet(
        sourceSet = sourceSet,
        module = ModuleId("r//:app"),
        files = listOf(file),
        syntheticSources = listOf(SyntheticSource("gen/R.kt", "kotlin", "object R", sourceSet, "android.resources")),
        emitFactsFor = true,
        fingerprint = Fingerprint("ef".repeat(32)),
    )

    private val contextOnlySourceSet = workerSourceSet.copy(
        sourceSet = SourceSetId("r//:app@debug"),
        files = emptyList(),
        syntheticSources = emptyList(),
        emitFactsFor = false,
    )

    private val module = WorkerModule(
        name = "r//:app@main+debug",
        platform = Platform.ANDROID,
        sourceSets = listOf(workerSourceSet, contextOnlySourceSet),
        dependsOn = listOf("r//:app@commonMain"),
        dependencies = listOf("r//:lib@main"),
        friends = listOf("r//:app@main"),
        classpath = listOf("/libs/a.jar"),
    )

    private val outline = ScriptOutline(
        listOf(
            ScriptCall(
                name = "plugins",
                receiver = null,
                arguments = emptyList(),
                body = listOf(
                    ScriptCall(
                        "id",
                        null,
                        listOf(ScriptArgument(null, "java", "\"java\"")),
                        emptyList(),
                        emptyMap(),
                        range(),
                    ),
                ),
                assignments = mapOf("group" to "\"g\""),
                range = range(),
            ),
        ),
    )

    private val requests: List<WorkerRequest> = listOf(
        WorkerRequest.Handshake(WorkerProtocol.PROTOCOL_VERSION),
        WorkerRequest.Analyze("a-1", listOf(module)),
        WorkerRequest.Declare("d-1", listOf(module)),
        WorkerRequest.OutlineScripts("o-1", listOf(file)),
        WorkerRequest.Shutdown,
    )

    private val responses: List<WorkerResponse> = listOf(
        WorkerResponse.Ready(
            WorkerProtocol.PROTOCOL_VERSION,
            ProducerRef("kotlin-worker", "1.0.0"),
            setOf(AnalyzerCapability.DECLARATIONS, AnalyzerCapability.CALLS),
        ),
        WorkerResponse.Batch("a-1", batch()),
        WorkerResponse.Outline("o-1", ref, outline),
        WorkerResponse.Completed("a-1"),
        WorkerResponse.Failed("a-1", ReasonCode("worker.out-of-memory"), "Heap exhausted"),
        WorkerResponse.Failed(null, ReasonCode("worker.protocol"), "Unreadable request"),
    )

    @Test
    fun `protocol is at version four`() {
        assertEquals(4, WorkerProtocol.PROTOCOL_VERSION)
    }

    @Test
    fun `every request survives encoding and decoding`() {
        requests.forEach { assertEquals(it, WorkerProtocol.decodeRequest(WorkerProtocol.encode(it))) }
    }

    @Test
    fun `a module with several source sets keeps their order and ownership flags`() {
        val decoded = WorkerProtocol.decodeRequest(WorkerProtocol.encode(WorkerRequest.Analyze("a-2", listOf(module))))
        val sourceSets = (decoded as WorkerRequest.Analyze).modules.single().sourceSets
        assertEquals(listOf(true, false), sourceSets.map { it.emitFactsFor })
        assertEquals(listOf(sourceSet, SourceSetId("r//:app@debug")), sourceSets.map { it.sourceSet })
    }

    @Test
    fun `every response survives encoding and decoding`() {
        responses.forEach { assertEquals(it, WorkerProtocol.decodeResponse(WorkerProtocol.encode(it))) }
    }

    @Test
    fun `every message fits on a single line even when it contains line breaks`() {
        val lines = requests.map(WorkerProtocol::encode) + responses.map(WorkerProtocol::encode)
        lines.forEach { assertFalse(it.contains('\n') || it.contains('\r'), it) }
    }

    @Test
    fun `messages use stable lowercase discriminators`() {
        assertEquals("{\"type\":\"handshake\",\"protocolVersion\":4}", WorkerProtocol.encode(requests[0]))
        assertEquals("{\"type\":\"shutdown\"}", WorkerProtocol.encode(WorkerRequest.Shutdown))
        assertEquals("{\"type\":\"completed\",\"requestId\":\"a-1\"}", WorkerProtocol.encode(responses[3]))
    }

    private fun range() = TextRange(1, 1, 1, 10)

    private fun batch() = FactBatch(
        ProducerRef("kotlin-worker", "1.0.0"),
        OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.value),
        Fingerprint("cd".repeat(32)),
        emptyList(),
    )
}

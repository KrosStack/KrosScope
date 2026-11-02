package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkerLoopTest {
    private class Session(val exitCode: Int, val responses: List<WorkerResponse>)

    private fun serve(vararg lines: String): Session {
        val output = StringWriter()
        val writer = ResponseWriter(output)
        val input = BufferedReader(StringReader(lines.joinToString("\n")))
        val code = WorkerLoop(input, writer, WorkerService(writer)).run()
        return Session(code, output.toString().lines().filter { it.isNotBlank() }.map(WorkerProtocol::decodeResponse))
    }

    private fun line(request: WorkerRequest) = WorkerProtocol.encode(request)

    @Test
    fun `a handshake is answered with the protocol version, producer and capabilities`() {
        val session = serve(line(WorkerRequest.Handshake(WorkerProtocol.PROTOCOL_VERSION)))
        val ready = assertIs<WorkerResponse.Ready>(session.responses.single())
        assertEquals(WorkerProtocol.PROTOCOL_VERSION, ready.protocolVersion)
        assertEquals("kscope.analyzer.kotlin", ready.producer.id)
        val expected = setOf(
            AnalyzerCapability.DECLARATIONS,
            AnalyzerCapability.RESOLVED_REFERENCES,
            AnalyzerCapability.CALLS,
            AnalyzerCapability.INHERITANCE,
            AnalyzerCapability.SCRIPT_OUTLINE,
            AnalyzerCapability.API_DIGEST,
        )
        assertEquals(expected, ready.capabilities)
    }

    @Test
    fun `end of input ends the loop successfully`() {
        assertEquals(EXIT_OK, serve().exitCode)
    }

    @Test
    fun `shutdown ends the loop and later requests are not read`() {
        val session = serve(line(WorkerRequest.Shutdown), line(WorkerRequest.Handshake(1)))
        assertEquals(EXIT_OK, session.exitCode)
        assertEquals(emptyList(), session.responses)
    }

    @Test
    fun `blank lines between requests are ignored`() {
        val session = serve("", line(WorkerRequest.Handshake(1)), "  ", line(WorkerRequest.Shutdown))
        assertEquals(1, session.responses.size)
    }

    @Test
    fun `an unreadable request fails the worker with a protocol failure`() {
        val session = serve("this is not json", line(WorkerRequest.Handshake(1)))
        val failed = assertIs<WorkerResponse.Failed>(session.responses.single())
        assertEquals(EXIT_FAILED, session.exitCode)
        assertEquals("worker.protocol", failed.reason.value)
        assertEquals(null, failed.requestId)
    }

    @Test
    fun `a script that cannot be read fails its request and the worker keeps serving`() {
        val missing = WorkerFile(
            ArtifactRef(RepositoryId("r"), RepoPath("missing/build.gradle.kts")),
            SampleWorkspace.samplesRoot.resolve("does-not-exist.gradle.kts").toString(),
            ContentHash("0".repeat(64)),
        )
        val outline = line(WorkerRequest.OutlineScripts("o-1", listOf(missing)))
        val session = serve(outline, line(WorkerRequest.Handshake(1)))
        val failed = assertIs<WorkerResponse.Failed>(session.responses.first())
        assertEquals("o-1", failed.requestId)
        assertEquals("kotlin.script-unreadable", failed.reason.value)
        assertTrue(session.responses.last() is WorkerResponse.Ready)
        assertEquals(EXIT_OK, session.exitCode)
    }

    @Test
    fun `a declare request is answered with batches of declarations and then completed`() {
        val module = SampleWorkspace.singleModule("mixed", "mixed/main")
        val session = serve(line(WorkerRequest.Declare("d-1", listOf(module))))
        val batches = session.responses.filterIsInstance<WorkerResponse.Batch>()
        assertEquals(module.sourceSets.map { it.sourceSet.value }, batches.map { it.batch.owner.key })
        assertTrue(batches.all { it.requestId == "d-1" })
        assertEquals(WorkerResponse.Completed("d-1"), session.responses.last())
        assertEquals(EXIT_OK, session.exitCode)
    }
}

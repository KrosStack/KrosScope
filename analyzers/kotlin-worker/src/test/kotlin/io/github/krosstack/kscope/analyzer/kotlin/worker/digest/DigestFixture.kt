package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import io.github.krosstack.kscope.analyzer.kotlin.worker.ResponseWriter
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerLoop
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerService
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

internal class DigestFixture(private val root: Path) {
    fun file(name: String, text: String? = null): WorkerFile {
        val path = root.resolve(name)
        if (text != null) Files.writeString(path, text)
        return WorkerFile(ArtifactRef(RepositoryId("sample"), RepoPath(name)), path.toString(), ContentHash("content"))
    }

    fun responses(sources: List<WorkerFile>): List<WorkerResponse> {
        val output = StringWriter()
        val writer = ResponseWriter(output)
        val request = WorkerProtocol.encode(WorkerRequest.DigestApi("d-1", sources))
        val input = StringReader(request + "\n" + WorkerProtocol.encode(WorkerRequest.Shutdown)).buffered()
        assertEquals(0, WorkerLoop(input, writer, WorkerService(writer)).run())
        return output.toString().lineSequence().filter { it.isNotBlank() }.map(WorkerProtocol::decodeResponse).toList()
    }

    fun digests(sources: List<WorkerFile>): Map<ArtifactRef, String> {
        val responses = responses(sources)
        assertEquals(WorkerResponse.Completed("d-1"), responses.last())
        return responses.filterIsInstance<WorkerResponse.ApiDigest>()
            .mapNotNull { answer -> answer.digest?.let { answer.ref to it } }.toMap()
    }
}

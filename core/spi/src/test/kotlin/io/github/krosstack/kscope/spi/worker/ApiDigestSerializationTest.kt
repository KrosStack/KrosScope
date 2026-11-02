package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiDigestSerializationTest {
    private val ref = ArtifactRef(RepositoryId("sample"), RepoPath("Api.kt"))

    @Test
    fun `an API digest request survives encoding and decoding`() {
        val request = WorkerRequest.DigestApi("d-1", listOf(WorkerFile(ref, "Api.kt", ContentHash("content"))))
        val encoded = WorkerProtocol.encode(request)
        assertTrue(encoded.startsWith("{\"type\":\"digest-api\","))
        assertEquals(request, WorkerProtocol.decodeRequest(encoded))
    }

    @Test
    fun `an API digest response survives encoding and decoding`() {
        val response = WorkerResponse.ApiDigest("d-1", ref, "ab".repeat(32))
        val encoded = WorkerProtocol.encode(response)
        assertTrue(encoded.startsWith("{\"type\":\"api-digest\","))
        assertEquals(response, WorkerProtocol.decodeResponse(encoded))
    }

    @Test
    fun `an answer without a digest survives encoding and decoding`() {
        val response = WorkerResponse.ApiDigest("d-1", ref, null)
        val encoded = WorkerProtocol.encode(response)
        assertTrue(encoded.endsWith("\"digest\":null}"), encoded)
        assertEquals(response, WorkerProtocol.decodeResponse(encoded))
    }
}

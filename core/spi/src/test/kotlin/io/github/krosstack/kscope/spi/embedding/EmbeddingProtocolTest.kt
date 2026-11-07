package io.github.krosstack.kscope.spi.embedding

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmbeddingProtocolTest {
    @Test
    fun `every request round trips through one line`() {
        val requests = listOf(
            EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION),
            EmbeddingRequest.Embed("request", listOf("first\nsecond", "Unicode: λ", "")),
            EmbeddingRequest.Cancel("request"),
            EmbeddingRequest.Shutdown,
        )
        val lines = requests.joinToString("\n") { EmbeddingProtocol.encode(it) }

        assertEquals(requests, lines.lineSequence().map(EmbeddingProtocol::decodeRequest).toList())
    }

    @Test
    fun `ready and failure responses round trip through one line`() {
        val responses = listOf(
            EmbeddingResponse.Ready(EmbeddingProtocol.VERSION, ModelIdentity("model", "rev", 2, "int8", 1)),
            EmbeddingResponse.Failed("request", "first\nsecond"),
            EmbeddingResponse.Failed(null, "unavailable"),
        )
        val lines = responses.joinToString("\n") { EmbeddingProtocol.encode(it) }

        assertEquals(responses, lines.lineSequence().map(EmbeddingProtocol::decodeResponse).toList())
    }

    @Test
    fun `vectors retain their float contents through one line`() {
        val response = EmbeddingResponse.Vectors("request", listOf(floatArrayOf(-1.25f, 0f, 1f), floatArrayOf()))
        val line = EmbeddingProtocol.encode(response)
        val decoded = line.lineSequence().map(EmbeddingProtocol::decodeResponse).single() as EmbeddingResponse.Vectors

        assertEquals(
            response.requestId to response.vectors.map { it.toList() },
            decoded.requestId to decoded.vectors.map { it.toList() },
        )
    }

    @Test
    fun `unknown request type fails with the decoder exception`() {
        assertFailsWith<SerializationException> {
            EmbeddingProtocol.decodeRequest("""{"type":"unknown"}""")
        }
    }

    @Test
    fun `unknown response type fails with the decoder exception`() {
        assertFailsWith<SerializationException> {
            EmbeddingProtocol.decodeResponse("""{"type":"unknown"}""")
        }
    }
}

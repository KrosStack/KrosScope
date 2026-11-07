package io.github.krosstack.kscope.spi.embedding

import io.github.krosstack.kscope.model.KscopeJson

/** Messages travel as one compact JSON document per line over the worker's standard streams. */
public object EmbeddingProtocol {
    public const val VERSION: Int = 1

    public fun encode(request: EmbeddingRequest): String = KscopeJson.encodeToString<EmbeddingRequest>(request)

    public fun encode(response: EmbeddingResponse): String = KscopeJson.encodeToString<EmbeddingResponse>(response)

    public fun decodeRequest(line: String): EmbeddingRequest = KscopeJson.decodeFromString<EmbeddingRequest>(line)

    public fun decodeResponse(line: String): EmbeddingResponse = KscopeJson.decodeFromString<EmbeddingResponse>(line)
}

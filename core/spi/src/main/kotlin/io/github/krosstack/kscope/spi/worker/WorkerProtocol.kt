package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.KscopeJson

/** Messages travel as one compact JSON document per line over the worker's standard streams. */
public object WorkerProtocol {
    public const val PROTOCOL_VERSION: Int = 4

    public fun encode(request: WorkerRequest): String = KscopeJson.encodeToString<WorkerRequest>(request)

    public fun encode(response: WorkerResponse): String = KscopeJson.encodeToString<WorkerResponse>(response)

    public fun decodeRequest(line: String): WorkerRequest = KscopeJson.decodeFromString<WorkerRequest>(line)

    public fun decodeResponse(line: String): WorkerResponse = KscopeJson.decodeFromString<WorkerResponse>(line)
}

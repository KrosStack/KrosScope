package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import kotlin.system.exitProcess

internal object FakeApiDigests {
    fun answer(mode: String, request: WorkerRequest.DigestApi) {
        if (mode == "exit") exitProcess(1)
        val sources = if (mode == "fail") request.sources.take(1) else request.sources
        sources.forEach { send(WorkerResponse.ApiDigest(request.requestId, it.ref, "ab".repeat(32))) }
        val conclusion = if (mode == "fail") {
            WorkerResponse.Failed(request.requestId, ReasonCode("worker.analysis-failed"), "kaput")
        } else {
            WorkerResponse.Completed(request.requestId)
        }
        send(conclusion)
    }

    private fun send(response: WorkerResponse) {
        println(WorkerProtocol.encode(response))
        System.out.flush()
    }
}

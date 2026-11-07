package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.semantic.worker.tokenizer.WordPieceTokenizer
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class WorkerLoop(
    private val input: BufferedReader,
    private val output: BufferedWriter,
    private val descriptor: ModelDescriptor,
    private val tokenizer: WordPieceTokenizer,
    private val shutdownGraceMillis: Long = TimeUnit.SECONDS.toMillis(BATCH_DEADLINE_SECONDS),
) {
    private val active = AtomicReference<String?>()
    private val cancelled = AtomicReference<String?>()
    private val executor = Executors.newSingleThreadExecutor {
        Thread(it, "semantic-inference").apply { isDaemon = true }
    }

    fun run(embed: (List<String>) -> List<FloatArray>, exit: (Int) -> Unit = Runtime.getRuntime()::halt) {
        try {
            var line = input.readLine()
            while (line != null && handle(line, embed)) line = input.readLine()
        } finally {
            cancelled.set(active.get())
            executor.shutdown()
            if (!executor.awaitTermination(shutdownGraceMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
                System.err.println("Embedding did not stop within shutdown grace")
                exit(1)
            }
        }
    }

    private fun handle(line: String, embed: (List<String>) -> List<FloatArray>): Boolean = try {
        when (val request = EmbeddingProtocol.decodeRequest(line)) {
            is EmbeddingRequest.Handshake -> {
                handshake(request)
                true
            }

            is EmbeddingRequest.Embed -> {
                submit(request, embed)
                true
            }

            is EmbeddingRequest.Cap -> {
                guarded(request.requestId) {
                    send(EmbeddingResponse.Capped(request.requestId, tokenizer.cap(request.text, request.maxTokens)))
                }
                true
            }

            is EmbeddingRequest.Cancel -> {
                if (active.get() == request.requestId) cancelled.set(request.requestId)
                true
            }

            EmbeddingRequest.Shutdown -> false
        }
    } catch (failure: kotlinx.serialization.SerializationException) {
        send(EmbeddingResponse.Failed(null, failure.message.orEmpty()))
        true
    }

    private fun handshake(request: EmbeddingRequest.Handshake) {
        if (request.protocolVersion == EmbeddingProtocol.VERSION) {
            send(EmbeddingResponse.Ready(EmbeddingProtocol.VERSION, descriptor.identity))
        } else {
            send(EmbeddingResponse.Failed(null, "Unsupported embedding protocol"))
        }
    }

    private fun submit(request: EmbeddingRequest.Embed, embed: (List<String>) -> List<FloatArray>) {
        if (!active.compareAndSet(null, request.requestId)) {
            send(EmbeddingResponse.Failed(request.requestId, "Embedding already in progress"))
            return
        }
        executor.execute {
            try {
                guarded(request.requestId) { answer(request, embed) }
            } finally {
                cancelled.compareAndSet(request.requestId, null)
            }
        }
    }

    private fun answer(request: EmbeddingRequest.Embed, embed: (List<String>) -> List<FloatArray>) {
        val vectors = ArrayList<FloatArray>()
        request.texts.chunked(OnnxEmbedder.BATCH_SIZE).forEach { batch ->
            check(cancelled.get() != request.requestId) { "Embedding cancelled" }
            vectors.addAll(embed(batch.map { if (request.query) descriptor.prefix + it else it }))
        }
        check(cancelled.get() != request.requestId) { "Embedding cancelled" }
        active.set(null)
        send(EmbeddingResponse.Vectors(request.requestId, vectors))
    }

    private fun guarded(id: String, action: () -> Unit) {
        val failure = try {
            action()
            return
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            failure.message.orEmpty()
        } catch (failure: IllegalStateException) {
            failure.message.orEmpty()
        } catch (failure: ai.onnxruntime.OrtException) {
            System.err.println(failure.message)
            "ONNX inference failed"
        }
        active.compareAndSet(id, null)
        send(EmbeddingResponse.Failed(id, failure))
    }

    @Synchronized
    private fun send(response: EmbeddingResponse) {
        output.write(EmbeddingProtocol.encode(response))
        output.newLine()
        output.flush()
    }

    private companion object {
        const val BATCH_DEADLINE_SECONDS = 120L
    }
}

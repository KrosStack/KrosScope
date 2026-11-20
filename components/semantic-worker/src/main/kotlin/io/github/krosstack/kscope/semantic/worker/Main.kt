@file:JvmName("Main")

package io.github.krosstack.kscope.semantic.worker

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtProvider
import io.github.krosstack.kscope.semantic.worker.tokenizer.TokenizerFile
import io.github.krosstack.kscope.semantic.worker.tokenizer.WordPieceTokenizer
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import java.io.BufferedWriter
import java.io.FileDescriptor
import java.io.FileOutputStream

public fun main(args: Array<String>) {
    val protocol = FileOutputStream(FileDescriptor.out).bufferedWriter(Charsets.UTF_8)
    System.setOut(System.err)
    val arguments = WorkerArguments(args)
    try {
        runWorker(arguments, protocol)
    } catch (failure: OrtException) {
        startupFailure(protocol, arguments.device, failure)
    } catch (failure: LinkageError) {
        startupFailure(protocol, arguments.device, failure)
    }
}

private fun runWorker(arguments: WorkerArguments, protocol: BufferedWriter) {
    if (arguments.device == "cuda" && OrtProvider.CUDA !in OrtEnvironment.getAvailableProviders()) {
        throw OrtException("CUDA provider is missing from this runtime")
    }
    val files = ModelFiles(arguments.model)
    val tokenizer = WordPieceTokenizer(TokenizerFile(files.tokenizer), files.descriptor.maxTokens)
    OnnxEmbedder(files, tokenizer, arguments.device).use { embedder ->
        WorkerLoop(
            System.`in`.bufferedReader(Charsets.UTF_8),
            protocol,
            files.descriptor.copy(revision = files.pipelineIdentity(arguments.device).revision),
            tokenizer,
        )
            .run(embedder::embed)
    }
}

private fun startupFailure(protocol: BufferedWriter, device: String, failure: Throwable) {
    val dependencies = if (device == "cuda") {
        " Requires onnxruntime_gpu, an NVIDIA driver, CUDA 13 (cublas/cudart) and cuDNN 9 on PATH or LD_LIBRARY_PATH."
    } else {
        ""
    }
    val causes = generateSequence(failure) { it.cause }.joinToString(": ") {
        "${it.javaClass.simpleName}: ${it.message.orEmpty()}"
    }
    val reason = "$device initialization failed.$dependencies $causes"
    protocol.write(EmbeddingProtocol.encode(EmbeddingResponse.Failed(null, reason)))
    protocol.newLine()
    protocol.flush()
}

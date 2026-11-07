package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.nio.file.Path
import kotlin.io.path.writeText

object FakeEmbeddingMain {
    @JvmStatic
    fun main(args: Array<String>) {
        Path.of(args[0]).writeText(ProcessHandle.current().pid().toString())
        if (args[1] in setOf("child", "exit", "long-startup", "many-causes")) {
            when (args[1]) {
                "exit" -> System.err.println("Native runtime could not initialize")

                "long-startup" -> {
                    System.err.println("Startup failure: " + "x".repeat(4096))
                    System.err.println("Caused by: IllegalStateException: Missing runtime dependency")
                }

                "many-causes" -> {
                    System.err.println("Startup failure: runtime")
                    repeat(args.getOrElse(2) { "20" }.toInt()) { System.err.println("Caused by: failure $it") }
                }

                else -> {
                    println("ready")
                    System.`in`.read()
                }
            }
            return
        }
        if (args[1] in setOf("silent-cause", "stderr-forever")) {
            System.err.println("Caused by: IllegalStateException: Missing runtime dependency")
        }
        val child = child(args)
        try {
            val input = System.`in`.bufferedReader()
            var line = input.readLine()
            while (line != null) {
                val request = EmbeddingProtocol.decodeRequest(line)
                if (request == EmbeddingRequest.Shutdown) return
                answer(request, args[1])?.let { println(EmbeddingProtocol.encode(it)) }
                line = input.readLine()
            }
        } finally {
            child?.destroyForcibly()
        }
    }

    private fun child(args: Array<String>): Process? = if (args[1] == "silent-tree") {
        ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("java.class.path"),
            FakeEmbeddingMain::class.java.name,
            args[0] + "-child",
            "child",
        ).start().also { check(it.inputStream.bufferedReader().readLine() == "ready") }
    } else {
        null
    }

    private fun answer(request: EmbeddingRequest, mode: String): EmbeddingResponse? = when (request) {
        is EmbeddingRequest.Handshake -> EmbeddingResponse.Ready(
            EmbeddingProtocol.VERSION,
            ModelIdentity("fake", if (mode == "mismatch") "wrong" else "test", 2, "int8-dynamic", 1),
        )

        is EmbeddingRequest.Embed -> embedding(request, mode)

        is EmbeddingRequest.Cap -> EmbeddingResponse.Capped(request.requestId, request.text.take(request.maxTokens))

        is EmbeddingRequest.Cancel -> null

        EmbeddingRequest.Shutdown -> null
    }

    private fun embedding(request: EmbeddingRequest.Embed, mode: String): EmbeddingResponse? = when {
        mode == "stderr-forever" -> {
            while (!System.err.checkError()) System.err.println("at noisy.runtime.Frame.run(Frame.java:1)")
            null
        }

        mode.startsWith("silent") -> {
            System.`in`.read()
            null
        }

        mode == "bad" -> EmbeddingResponse.Vectors("wrong-id", emptyList())

        mode == "inference-failed" -> {
            System.err.println("Caused by: IllegalStateException: Missing runtime dependency")
            EmbeddingResponse.Failed(request.requestId, "ONNX inference failed")
        }

        else -> EmbeddingResponse.Vectors(
            request.requestId,
            request.texts.map {
                floatArrayOf(if (request.query) 1f else 0f, it.length.toFloat())
            },
        )
    }
}

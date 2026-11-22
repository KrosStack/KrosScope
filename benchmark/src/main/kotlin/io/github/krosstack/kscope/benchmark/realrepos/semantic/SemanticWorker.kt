package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class SemanticWorker(options: SemanticOptions) :
    EmbeddingProvider,
    AutoCloseable {
    override val id = "codeRankEmbed"
    val recipe = fingerprint(options)
    private val process = ProcessBuilder(command(options)).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    private val input = process.inputStream.bufferedReader(Charsets.UTF_8)
    private val output = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val exchanges = Executors.newSingleThreadExecutor {
        Thread(
            it,
            "benchmark-embedding",
        ).apply { isDaemon = true }
    }
    private var sequence = 0
    private val cleanup = Thread({ process.destroyForcibly() }, "benchmark-worker-cleanup")
    private var closed = false
    override val identity: ModelIdentity

    init {
        Runtime.getRuntime().addShutdownHook(cleanup)
        identity = runCatching {
            val ready = exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION))
            require(ready is EmbeddingResponse.Ready && ready.protocolVersion == EmbeddingProtocol.VERSION)
            val descriptor = DESCRIPTOR_JSON.decodeFromString<ModelIdentity>(
                Files.readString(Path.of(options.flags.getValue("--model"), "model.json")),
            )
            val device = options.flags.getValue("--device")
            val revision = ready.identity.revision
            val pipeline = "${descriptor.revision};pipeline-v1:$device:${descriptor.quantization}:"
            require(ready.identity.copy(revision = descriptor.revision) == descriptor && descriptor.dimensions > 0)
            require((revision == descriptor.revision && device == "cpu") || revision.startsWith(pipeline)) {
                "Worker identity $revision does not match the $device pipeline of ${descriptor.revision}"
            }
            ready.identity.copy(revision = "${ready.identity.revision}:${SemanticCheckpoint.digest(recipe)}")
        }.getOrElse { failure ->
            close()
            if (failure is Error || failure is InterruptedException) throw failure
            throw IOException("Semantic worker handshake failed", failure)
        }
    }

    override fun cap(text: String, maxTokens: Int): String {
        val request = EmbeddingRequest.Cap((++sequence).toString(), text, maxTokens)
        val response = exchange(request)
        require(response is EmbeddingResponse.Capped && response.requestId == request.requestId)
        return response.text
    }

    override fun embed(texts: List<String>): List<FloatArray> = vectors(texts, false)

    override fun embedQuery(text: String): FloatArray = vectors(listOf(text), true).single()

    private fun vectors(texts: List<String>, query: Boolean): List<FloatArray> {
        val request = EmbeddingRequest.Embed((++sequence).toString(), texts, query)
        val response = exchange(request)
        require(response is EmbeddingResponse.Vectors && response.requestId == request.requestId)
        require(
            response.vectors.size == texts.size && response.vectors.all {
                it.size == identity.dimensions && it.all(Float::isFinite)
            },
        )
        return response.vectors
    }

    private fun exchange(request: EmbeddingRequest): EmbeddingResponse {
        val pending = exchanges.submit<EmbeddingResponse> {
            output.write(EmbeddingProtocol.encode(request) + "\n")
            output.flush()
            EmbeddingProtocol.decodeResponse(requireNotNull(input.readLine()) { "Semantic worker exited" })
        }
        return runCatching { pending.get(EXCHANGE_SECONDS, TimeUnit.SECONDS) }.getOrElse { failure ->
            close()
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            if (failure is Error || failure is InterruptedException) throw failure
            throw IOException("Semantic worker exchange failed", failure)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        process.destroyForcibly()
        process.waitFor(EXCHANGE_SECONDS, TimeUnit.SECONDS)
        exchanges.shutdownNow()
        Runtime.getRuntime().removeShutdownHook(cleanup)
    }

    private companion object {
        const val EXCHANGE_SECONDS = 120L
        val DESCRIPTOR_JSON = Json { ignoreUnknownKeys = true }

        fun command(options: SemanticOptions): List<String> {
            val worker = Path.of(options.flags.getValue("--worker")).toAbsolutePath()
            val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
            val device = options.flags.getValue("--device")
            return listOf(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Donnxruntime.native.path=${worker.resolve("native/$platform")}",
                "-cp",
                "${worker.resolve("lib")}/*",
                "io.github.krosstack.kscope.semantic.worker.Main",
                Path.of(options.flags.getValue("--model")).toAbsolutePath().toString(),
            ) +
                if (device == "cpu") emptyList() else listOf(device)
        }

        fun fingerprint(options: SemanticOptions): String {
            val files: MutableMap<String, String> = sortedMapOf(
                "device" to options.flags.getValue("--device"),
                "platform" to System.getProperty("os.name") + System.getProperty("os.arch"),
                "java" to System.getProperty("java.runtime.version"),
                "policy" to "protocol1-builder1-doc16-query1-rrf60-maxfile",
            )
            for (flag in listOf("--worker", "--model")) {
                val root = Path.of(options.flags.getValue(flag))
                files.putAll(inventory(root).mapKeys { "$flag/${it.key}" })
            }
            return KscopeJson.encodeToString<Map<String, String>>(files)
        }

        fun inventory(root: Path): Map<String, String> = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.sorted().toList().associate { path ->
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(path).use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    generateSequence { stream.read(buffer).takeIf { it >= 0 } }.forEach { digest.update(buffer, 0, it) }
                }
                root.relativize(path).toString().replace('\\', '/') to HexFormat.of().formatHex(digest.digest())
            }
        }
    }
}

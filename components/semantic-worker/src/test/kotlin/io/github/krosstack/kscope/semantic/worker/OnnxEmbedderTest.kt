package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.semantic.worker.tokenizer.TokenizerFile
import io.github.krosstack.kscope.semantic.worker.tokenizer.WordPieceTokenizer
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.PipedReader
import java.io.PipedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class OnnxEmbedderTest {
    @Test
    fun `real model token ids match every Python reference`() {
        val matches = withModel { ids, _, reference ->
            reference.getValue("ids").jsonArray.map { it.jsonPrimitive.long } == ids
        }
        assertTrue(matches.all { it }, "Reference token id matches: $matches")
    }

    @Test
    fun `real model vectors have unit norm`() {
        val norms = withModel { _, vector, _ -> sqrt(vector.sumOf { it.toDouble() * it }) }
        assertTrue(norms.all { abs(it - 1.0) <= 0.00001 }, "Vector norms: $norms")
    }

    @Test
    fun `real model document and query vectors agree with Python references`() {
        val cosines = withModel { _, vector, reference ->
            cosine(vector, reference.getValue("vector").jsonArray.map { it.jsonPrimitive.float })
        }
        assertTrue(cosines.all { it >= 0.999 }, "Document and query cosines: $cosines")
    }

    private fun <T> withModel(inspect: (List<Long>, FloatArray, JsonObject) -> T): List<T> {
        val directory = System.getenv("KSCOPE_SEMANTIC_MODEL")?.let(Path::of)
        assumeTrue(directory != null && Files.exists(directory.resolve("model.json")), "Exported model is absent")
        val files = ModelFiles(requireNotNull(directory))
        val tokenizer = WordPieceTokenizer(TokenizerFile(files.tokenizer), files.descriptor.maxTokens)
        return OnnxEmbedder(files, tokenizer).use { embedder ->
            val requests = PipedWriter()
            val responses = PipedWriter()
            val output = requests.buffered()
            val input = PipedReader(responses).buffered()
            val loop = WorkerLoop(PipedReader(requests).buffered(), responses.buffered(), files.descriptor, tokenizer)
            val executor = Executors.newSingleThreadExecutor()
            val running = executor.submit { loop.run(embedder::embed) }
            try {
                references().mapIndexed { index, reference ->
                    val request = request(index, reference)
                    val text = request.texts.single().let { if (request.query) files.descriptor.prefix + it else it }
                    inspect(tokenizer.encode(text).ids.toList(), exchange(output, input, request), reference)
                }
            } finally {
                output.write(EmbeddingProtocol.encode(EmbeddingRequest.Shutdown) + "\n")
                output.flush()
                running.get(120, TimeUnit.SECONDS)
                executor.shutdownNow()
                input.close()
                output.close()
            }
        }
    }

    private fun references(): List<JsonObject> {
        val fixtures = requireNotNull(javaClass.getResource("/expected-vectors.json")).readText()
        return KscopeJson.parseToJsonElement(fixtures).jsonArray.map { it.jsonObject }
    }

    private fun request(index: Int, reference: JsonObject): EmbeddingRequest.Embed = EmbeddingRequest.Embed(
        index.toString(),
        listOf(reference.getValue("text").jsonPrimitive.content),
        reference.getValue("query").jsonPrimitive.boolean,
    )

    private fun exchange(output: BufferedWriter, input: BufferedReader, request: EmbeddingRequest.Embed): FloatArray {
        output.write(EmbeddingProtocol.encode(request) + "\n")
        output.flush()
        val reply = EmbeddingProtocol.decodeResponse(input.readLine())
        check(reply is EmbeddingResponse.Vectors)
        return reply.vectors.single()
    }

    private fun cosine(vector: FloatArray, expected: List<Float>): Double {
        if (expected.size != vector.size) return Double.NaN
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        val expectedNorm = sqrt(expected.sumOf { it.toDouble() * it })
        return vector.indices.sumOf { vector[it].toDouble() * expected[it] } / (norm * expectedNorm)
    }
}

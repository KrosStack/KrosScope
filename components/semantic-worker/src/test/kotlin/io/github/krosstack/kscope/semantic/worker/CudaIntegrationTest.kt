package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

class CudaIntegrationTest {
    @Test
    fun `cuda float vectors agree with cpu fp32`(@TempDir profiles: Path) {
        val libraries = System.getProperty("kscope.semantic.cuda.libraries")
        val model = System.getProperty("kscope.semantic.cuda.model")
        assumeTrue(libraries != null && model != null, "CUDA libraries and float model are not configured")
        val directory = Path.of(requireNotNull(model))
        val cpuDirectory = Path.of(System.getProperty("kscope.semantic.cuda.cpuModel", model))
        assertEquals("fp32", ModelFiles(cpuDirectory).descriptor.quantization)
        val texts = listOf("fun square(n: Int) = n * n", "class Repository", "find implementation")
        val expected = WorkerClient(cpuDirectory, "cpu").use { worker ->
            assertIs<EmbeddingResponse.Ready>(worker.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION)))
            vectors(worker, texts)
        }
        WorkerClient(
            directory,
            "cuda",
            gpuRuntime = true,
            libraries = libraries,
            configure = { command().add(1, "-Dkscope.semantic.profile=${profiles.resolve("cuda")}") },
        ).use { worker ->
            val reply = worker.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION))
            val ready = assertIs<EmbeddingResponse.Ready>(reply)
            assertTrue(ready.identity.revision.contains("pipeline-v1:cuda:"))
            vectors(worker, texts)
            val (actual, elapsed) = measureTimedValue { vectors(worker, texts) }
            val cosines = actual.zip(expected).map { (a, b) -> cosine(a, b) }
            assertEquals(texts.size, actual.size)
            assertTrue(cosines.all { it >= 0.999 }, "Cosines: $cosines")
            println("CUDA chunks/s=${texts.size / elapsed.inWholeNanoseconds.toDouble() * 1e9}; cosines=$cosines")
        }
        assertCudaExecution(profiles)
    }

    @Test
    fun `gpu runtime fails the handshake when no cuda device is visible`() {
        val libraries = System.getProperty("kscope.semantic.cuda.libraries")
        val model = System.getProperty(
            "kscope.semantic.cuda.cpuModel",
            System.getProperty("kscope.semantic.cuda.model"),
        )
        assumeTrue(libraries != null && model != null, "CUDA libraries and float model are not configured")
        val directory = Path.of(requireNotNull(model))
        assertEquals("fp32", ModelFiles(directory).descriptor.quantization)
        WorkerClient(
            directory,
            "cuda",
            gpuRuntime = true,
            libraries = libraries,
            configure = { environment()["CUDA_VISIBLE_DEVICES"] = "-1" },
        ).use { worker ->
            val failure = assertIs<EmbeddingResponse.Failed>(
                worker.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION)),
            )
            listOf(
                "cuda initialization failed",
                "CUDA failure 100",
                "no CUDA-capable device",
                "CUDA 13",
                "cuDNN 9",
            )
                .forEach { assertContains(failure.reason, it) }
            assertEquals(0, worker.awaitExit())
        }
    }

    private fun assertCudaExecution(directory: Path) {
        val profile = Files.list(directory).use { paths -> paths.toList().single() }
        val nodes = KscopeJson.parseToJsonElement(profile.readText()).jsonArray.map { it.jsonObject }
            .filter { it["cat"]?.jsonPrimitive?.content == "Node" }
        val cudaNodes = nodes.count {
            it["args"]?.jsonObject?.get("provider")?.jsonPrimitive?.content == "CUDAExecutionProvider"
        }
        assertTrue(cudaNodes > 0, "No inference nodes executed on CUDAExecutionProvider")
        println("CUDA profile executed $cudaNodes nodes on CUDAExecutionProvider")
    }

    private fun vectors(worker: WorkerClient, texts: List<String>): List<FloatArray> =
        assertIs<EmbeddingResponse.Vectors>(worker.exchange(EmbeddingRequest.Embed("vectors", texts))).vectors

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        assertEquals(a.size, b.size)
        return a.indices.sumOf { a[it].toDouble() * b[it] } /
            sqrt(a.sumOf { it.toDouble() * it } * b.sumOf { it.toDouble() * it })
    }
}

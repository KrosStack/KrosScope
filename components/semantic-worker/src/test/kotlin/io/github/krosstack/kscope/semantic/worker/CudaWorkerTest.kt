package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CudaWorkerTest {
    @Test
    fun `missing core native library returns a failed handshake and exits cleanly`(@TempDir directory: Path) {
        WorkerClient(
            directory,
            "cuda",
            gpuRuntime = true,
            configure = { command().add(1, "-Donnxruntime.native.path=$directory") },
        ).use { worker ->
            val failure = assertIs<EmbeddingResponse.Failed>(
                worker.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION)),
            )
            listOf("ExceptionInInitializerError", "IOException", "onnxruntime", "not found", "CUDA 13", "cuDNN 9")
                .forEach { assertContains(failure.reason, it) }
            assertEquals(0, worker.awaitExit())
        }
    }

    @Test
    fun `cuda without its runtime fails the handshake with dependency names`(@TempDir directory: Path) {
        WorkerClient(directory, "cuda").use { worker ->
            val reply = worker.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION))
            val failure = assertIs<EmbeddingResponse.Failed>(reply)
            listOf("cuda initialization failed", "onnxruntime_gpu", "CUDA 13", "cuDNN 9").forEach {
                assertContains(failure.reason, it)
            }
        }
    }

    @Test
    fun `gpu archive contains only x64 cuda natives and no cpu runtime`() {
        ZipFile(System.getProperty("kscope.semantic.cuda.archive")).use { zip ->
            val paths = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()
            val platforms = paths.filter { it.startsWith("native/") }.map { it.split('/')[1] }.toSet()
            assertEquals(setOf("windows-x64", "linux-x64"), platforms)
            assertTrue("native/windows-x64/onnxruntime_providers_cuda.dll" in paths)
            assertTrue("native/linux-x64/libonnxruntime_providers_cuda.so" in paths)
            assertTrue(paths.any { it.startsWith("lib/onnxruntime_gpu-1.31.1") })
            assertFalse(paths.any { it.startsWith("lib/onnxruntime-") })
        }
    }
}

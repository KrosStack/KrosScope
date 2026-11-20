package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class WorkerClient(
    model: Path,
    device: String,
    gpuRuntime: Boolean = false,
    libraries: String? = null,
    configure: ProcessBuilder.() -> Unit = {},
) : AutoCloseable {
    private val javaHome = System.getProperty("kscope.semantic.cuda.javaHome", System.getProperty("java.home"))
    private val process = ProcessBuilder(
        listOf(
            Path.of(javaHome, "bin", "java").toString(),
            "-cp",
            System.getProperty("kscope.semantic.${if (gpuRuntime) "cuda" else "cpu"}.classpath"),
            "io.github.krosstack.kscope.semantic.worker.Main",
            model.toString(),
            device,
        ).toMutableList(),
    ).apply {
        redirectError(ProcessBuilder.Redirect.INHERIT)
        environment()["JAVA_HOME"] = javaHome
        libraries?.let {
            val variable = if (System.getProperty("os.name").startsWith("Windows")) "PATH" else "LD_LIBRARY_PATH"
            environment()[variable] = it + java.io.File.pathSeparator + environment().getOrDefault(variable, "")
        }
        configure()
    }.start()
    private val executor = Executors.newSingleThreadExecutor()
    private val input = process.inputStream.bufferedReader(Charsets.UTF_8)
    private val output = process.outputStream.bufferedWriter(Charsets.UTF_8)

    fun exchange(request: EmbeddingRequest): EmbeddingResponse = executor.submit<EmbeddingResponse> {
        output.write(EmbeddingProtocol.encode(request) + "\n")
        output.flush()
        EmbeddingProtocol.decodeResponse(requireNotNull(input.readLine()))
    }.get(120, TimeUnit.SECONDS)

    fun awaitExit(): Int {
        check(process.waitFor(5, TimeUnit.SECONDS)) { "Inference process did not exit" }
        return process.exitValue()
    }

    override fun close() {
        executor.shutdownNow()
        output.close()
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
        input.close()
        check(!process.isAlive) { "Inference process did not exit" }
    }
}

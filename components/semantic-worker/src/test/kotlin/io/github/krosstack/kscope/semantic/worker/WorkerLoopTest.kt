package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.semantic.worker.tokenizer.TokenizerFile
import io.github.krosstack.kscope.semantic.worker.tokenizer.WordPieceTokenizer
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.PipedReader
import java.io.PipedWriter
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkerLoopTest {
    private val descriptor = ModelDescriptor("test", "revision", 2, "int8-dynamic", "cls", "query: ", 8192, emptyMap())
    private val tokenizer = WordPieceTokenizer(
        TokenizerFile(Path.of(requireNotNull(javaClass.getResource("/tokenizer.json")).toURI())),
        8192,
    )

    @Test
    fun `handshake reports model identity`() {
        serve({ texts -> texts.map { floatArrayOf(it.length.toFloat(), 1f) } }) { input, output ->
            send(output, EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION))
            assertEquals(descriptor.identity, assertIs<EmbeddingResponse.Ready>(read(input)).identity)
        }
    }

    @Test
    fun `query embedding includes the model prefix`() {
        serve({ texts -> texts.map { floatArrayOf(it.length.toFloat(), 1f) } }) { input, output ->
            send(output, EmbeddingRequest.Embed("one", listOf("code"), query = true))
            assertEquals(11f, assertIs<EmbeddingResponse.Vectors>(read(input)).vectors.single()[0])
        }
    }

    @Test
    fun `cap shortens text to the token budget`() {
        serve({ emptyList() }) { input, output ->
            send(output, EmbeddingRequest.Cap("cap", "hello world", 3))
            assertTrue(assertIs<EmbeddingResponse.Capped>(read(input)).text.length < 11)
        }
    }

    @Test
    fun `malformed line receives a failed response`() {
        serve({ emptyList() }) { input, output ->
            output.write("invalid\n")
            output.flush()
            assertIs<EmbeddingResponse.Failed>(read(input))
        }
    }

    @Test
    fun `shutdown during a batch exits within the grace bound`() {
        val entered = CountDownLatch(1)
        val release = java.util.concurrent.Semaphore(0)
        val requests = PipedWriter()
        val output = requests.buffered()
        val exitStatuses = mutableListOf<Int>()
        val loop = WorkerLoop(
            PipedReader(requests).buffered(),
            java.io.StringWriter().buffered(),
            descriptor,
            tokenizer,
            shutdownGraceMillis = 25,
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit {
                loop.run({
                    entered.countDown()
                    release.acquireUninterruptibly()
                    emptyList()
                }, exit = { exitStatuses.add(it) })
            }
            send(output, EmbeddingRequest.Embed("blocked", listOf("code")))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            send(output, EmbeddingRequest.Shutdown)
            running.get(1, TimeUnit.SECONDS)
            assertEquals(listOf(1), exitStatuses)
        } finally {
            release.release()
            executor.shutdownNow()
            output.close()
        }
    }

    @Test
    fun `normal shutdown returns without exiting`() {
        val exitStatuses = mutableListOf<Int>()
        val shutdown = EmbeddingProtocol.encode(EmbeddingRequest.Shutdown) + "\n"
        val loop = WorkerLoop(
            shutdown.reader().buffered(),
            java.io.StringWriter().buffered(),
            descriptor,
            tokenizer,
        )
        loop.run({ emptyList() }, exit = { exitStatuses.add(it) })
        assertEquals(emptyList(), exitStatuses)
    }

    @Test
    fun `inference failure becomes a failed line`() {
        serve({ error("inference unavailable") }) { input, output ->
            send(output, EmbeddingRequest.Embed("failure", listOf("code")))
            assertEquals("inference unavailable", assertIs<EmbeddingResponse.Failed>(read(input)).reason)
        }
    }

    @Test
    fun `cancellation received during inference prevents the next batch`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var batches = 0
        serve({ texts ->
            batches++
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            texts.map { floatArrayOf(1f, 0f) }
        }) { input, output ->
            send(output, EmbeddingRequest.Embed("cancel", List(17) { "code" }))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            send(output, EmbeddingRequest.Cancel("cancel"))
            send(output, EmbeddingRequest.Cap("barrier", "hello", 3))
            assertIs<EmbeddingResponse.Capped>(read(input))
            release.countDown()
            assertIs<EmbeddingResponse.Failed>(read(input))
            assertEquals(1, batches)
        }
    }

    private fun serve(
        embed: (List<String>) -> List<FloatArray>,
        assertions: (BufferedReader, BufferedWriter) -> Unit,
    ) {
        val requests = PipedWriter()
        val responses = PipedWriter()
        val input = PipedReader(responses).buffered()
        val output = requests.buffered()
        val loop = WorkerLoop(PipedReader(requests).buffered(), responses.buffered(), descriptor, tokenizer)
        val executor = Executors.newSingleThreadExecutor()
        val running = executor.submit { loop.run(embed) }
        try {
            assertions(input, output)
        } finally {
            send(output, EmbeddingRequest.Shutdown)
            running.get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
            input.close()
            output.close()
        }
    }

    private fun send(output: BufferedWriter, request: EmbeddingRequest) {
        output.write(EmbeddingProtocol.encode(request))
        output.newLine()
        output.flush()
    }

    private fun read(input: BufferedReader): EmbeddingResponse = EmbeddingProtocol.decodeResponse(input.readLine())
}

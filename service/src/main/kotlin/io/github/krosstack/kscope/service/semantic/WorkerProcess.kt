package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration

internal class WorkerProcess(command: List<String>, private val redact: (String) -> String = { it }) : AutoCloseable {
    private val process = try {
        ProcessBuilder(command).start()
    } catch (failure: IOException) {
        throw IOException(redact(failure.message.orEmpty()), failure)
    }
    private val closed = AtomicBoolean()
    private val input = process.inputStream.bufferedReader(Charsets.UTF_8)
    private val output = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val exchanges = Executors.newSingleThreadExecutor {
        Thread(it, "semantic-protocol").apply { isDaemon = true }
    }
    private val cleanup = Thread(::terminate, "semantic-cleanup")
    private val diagnostic = AtomicReference("")
    private var omittedCauses = 0
    private val forwarded = ArrayBlockingQueue<String>(DIAGNOSTIC_LINES)
    private val diagnostics = thread(isDaemon = true, name = "semantic-stderr") {
        process.errorStream.bufferedReader(Charsets.UTF_8).use { stream ->
            try {
                stream.forEachLine {
                    if (it.isNotBlank()) retainDiagnostic(redact(it).trim())
                    forwarded.offer(it)
                }
            } catch (_: IOException) {
                forwarded.offer("Semantic diagnostics stream closed")
            }
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(cleanup)
        thread(isDaemon = true, name = "semantic-stderr-forwarding") {
            val target = System.err
            while (!closed.get() || forwarded.isNotEmpty()) {
                forwarded.poll(DIAGNOSTIC_DRAIN_MILLIS, TimeUnit.MILLISECONDS)?.let { target.println(it) }
            }
        }
    }

    private fun retainDiagnostic(line: String) {
        val previous = diagnostic.get()
        val retained = (previous.removeSuffix(truncationMarker()) + "\n" + line).trim().lines().toMutableList()
        var truncated = previous.endsWith(TRUNCATED)
        while (retained.indices.count { it != 0 && !retained[it].startsWith("Caused by:") } > DIAGNOSTIC_LINES ||
            retained.joinToString("\n").length > DIAGNOSTIC_CHARACTERS - if (truncated) truncationMarker().length else 0
        ) {
            compactDiagnostic(retained)
            truncated = true
        }
        diagnostic.set(retained.joinToString("\n") + if (truncated) truncationMarker() else "")
    }

    private fun compactDiagnostic(retained: MutableList<String>) {
        val ordinary = retained.indices.filter { it != 0 && !retained[it].startsWith("Caused by:") }
        val discarded = ordinary.firstOrNull { retained[it].startsWith("at ") }
            ?: ordinary.firstOrNull()
        if (discarded != null) {
            retained.removeAt(discarded)
        } else {
            shortenProtectedDiagnostics(retained)
        }
    }

    private fun shortenProtectedDiagnostics(retained: MutableList<String>) {
        val budget = DIAGNOSTIC_CHARACTERS - truncationMarker().length - retained.size + 1
        var prefixCharacters = DIAGNOSTIC_PREFIX_CHARACTERS
        while (prefixCharacters > DIAGNOSTIC_MIN_PREFIX_CHARACTERS &&
            retained.sumOf { minOf(it.length, prefixCharacters) } > budget
        ) {
            prefixCharacters--
        }
        val prefixes = retained.map { minOf(it.length, prefixCharacters) }
        if (prefixes.sum() > budget) {
            retained.removeAt(1)
            omittedCauses++
            return
        }
        var lower = 0
        var upper = retained.maxOf { it.length }
        while (lower < upper) {
            val limit = lower + (upper - lower + 1) / 2
            val characters = retained.indices.sumOf { maxOf(prefixes[it], minOf(retained[it].length, limit)) }
            if (characters <= budget) lower = limit else upper = limit - 1
        }
        retained.indices.forEach { retained[it] = retained[it].take(maxOf(prefixes[it], lower)) }
    }

    private fun truncationMarker(): String =
        (if (omittedCauses == 0) "" else "\n[$omittedCauses cause headers omitted]") + TRUNCATED

    fun failureReason(reason: String): String {
        val failure = runCatching { close() }.exceptionOrNull()
        val retained = diagnostic.get()
        val original = if (retained.isNotEmpty() && reason.endsWith(retained)) {
            reason.removeSuffix(retained).removeSuffix("\n")
        } else {
            reason
        }
        val detail = listOf(redact(original), retained).filter { it.isNotBlank() }.joinToString("\n")
        if (failure != null) throw IOException(detail, failure)
        return detail
    }

    @Synchronized
    fun exchange(request: EmbeddingRequest, deadline: Duration): EmbeddingResponse {
        check(!closed.get()) { "Semantic process is closed" }
        val pending = exchanges.submit<EmbeddingResponse> {
            output.write(EmbeddingProtocol.encode(request))
            output.newLine()
            output.flush()
            EmbeddingProtocol.decodeResponse(input.readLine() ?: throw IOException("Semantic process exited"))
        }
        val failure = try {
            val reply = pending.get(deadline.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            return if (reply is EmbeddingResponse.Failed) reply.copy(reason = failureReason(reply.reason)) else reply
        } catch (failure: TimeoutException) {
            IOException("Semantic process deadline exceeded", failure)
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            cause as? IOException ?: IOException("Semantic process exchange failed: ${cause?.message}", failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            IOException("Semantic process exchange interrupted", failure)
        }
        val reason = runCatching { failureReason(failure.message.orEmpty()) }
        reason.exceptionOrNull()?.let(failure::addSuppressed)
        throw IOException(reason.getOrElse { it.message.orEmpty() }, failure)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var interrupted = false
        val steps = listOf<() -> Unit>(
            ::terminate,
            {
                try {
                    diagnostics.join(DIAGNOSTIC_DRAIN_MILLIS)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            },
            { exchanges.shutdownNow() },
            { input.close() },
            { output.close() },
            {
                try {
                    Runtime.getRuntime().removeShutdownHook(cleanup)
                } catch (_: IllegalStateException) {
                    // Shutdown is already running the registered hooks.
                }
            },
        )
        val failures = steps.map { step -> runCatching(step) }.mapNotNull { it.exceptionOrNull() }
        if (interrupted) Thread.currentThread().interrupt()
        failures.firstOrNull()?.let { failure ->
            failures.drop(1).forEach(failure::addSuppressed)
            throw failure
        }
    }

    private fun terminate() {
        val descendants = runCatching {
            val children = process.descendants().use { it.toList() }
            children.forEach { it.destroyForcibly() }
        }
        val root = runCatching { process.destroyForcibly() }
        descendants.exceptionOrNull()?.let { failure ->
            root.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        root.getOrThrow()
    }

    private companion object {
        const val DIAGNOSTIC_DRAIN_MILLIS = 1000L
        const val DIAGNOSTIC_LINES = 16
        const val DIAGNOSTIC_CHARACTERS = 2048
        const val DIAGNOSTIC_PREFIX_CHARACTERS = 160
        const val DIAGNOSTIC_MIN_PREFIX_CHARACTERS = 64
        const val TRUNCATED = "\n[stderr truncated]"
    }
}

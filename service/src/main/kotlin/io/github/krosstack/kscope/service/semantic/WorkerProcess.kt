package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.semantic.process.WorkerDiagnostics
import io.github.krosstack.kscope.service.semantic.query.QueryDeadline
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration

internal class WorkerProcess(
    command: List<String>,
    private val redact: (String) -> String = { it },
    routing: WorkerDiagnostics.Routing = WorkerDiagnostics.Routing(),
    deadline: QueryDeadline? = null,
    launch: (List<String>) -> Process = { ProcessBuilder(it).start() },
) : AutoCloseable {
    private val process = try {
        if (deadline?.expired() == true) throw IOException("Semantic query deadline expired before launch")
        launch(command)
    } catch (failure: IOException) {
        throw IOException(redact(failure.message.orEmpty()), failure)
    }
    private val closed = AtomicBoolean()
    private val exchangeLock = ReentrantLock()
    private val input = process.inputStream.bufferedReader(Charsets.UTF_8)
    private val output = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val exchanges = Executors.newSingleThreadExecutor {
        Thread(it, "semantic-protocol").apply { isDaemon = true }
    }
    private val cleanup = Thread(::terminate, "semantic-cleanup")
    private val diagnostic = AtomicReference("")
    private var omittedCauses = 0
    private val queried = AtomicBoolean(deadline != null)
    private val diagnostics = WorkerDiagnostics(
        process,
        routing,
        { line -> if (line.isNotBlank()) retainDiagnostic(redact(line).trim()) },
        closed::get,
        deadline != null,
    )
    private val indexingDiagnostics = routing.indexing

    init {
        Runtime.getRuntime().addShutdownHook(cleanup)
    }

    private fun retainDiagnostic(line: String) {
        val previous = diagnostic.get()
        val retained = (previous.removeSuffix(truncationMarker) + "\n" + line).trim().lines().toMutableList()
        var truncated = previous.endsWith(TRUNCATED)
        while (retained.indices.count { it != 0 && !retained[it].startsWith("Caused by:") } > DIAGNOSTIC_LINES ||
            retained.joinToString("\n").length > DIAGNOSTIC_CHARACTERS - if (truncated) truncationMarker.length else 0
        ) {
            compactDiagnostic(retained)
            truncated = true
        }
        diagnostic.set(retained.joinToString("\n") + if (truncated) truncationMarker else "")
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
        val budget = DIAGNOSTIC_CHARACTERS - truncationMarker.length - retained.size + 1
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

    private val truncationMarker: String
        get() = (if (omittedCauses == 0) "" else "\n[$omittedCauses cause headers omitted]") + TRUNCATED

    fun failureReason(reason: String, deadline: QueryDeadline? = null): String {
        diagnostics.drain(deadline)
        val failure = runCatching { close(deadline) }.exceptionOrNull()
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

    fun exchange(
        request: EmbeddingRequest,
        deadline: Duration,
        absoluteQuery: QueryDeadline? = null,
        queryDiagnostics: ((String) -> Unit)? = null,
        ownsProcess: Boolean = true,
    ): EmbeddingResponse {
        val limit = System.nanoTime() + deadline.inWholeNanoseconds
        val remaining = { absoluteQuery?.remaining()?.inWholeNanoseconds ?: maxOf(limit - System.nanoTime(), 0L) }
        val acquired = try {
            remaining().let { it > 0 && exchangeLock.tryLock(it, TimeUnit.NANOSECONDS) }
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Semantic process exchange interrupted", failure)
        }
        if (!acquired) {
            val reason = "Semantic process deadline exceeded while waiting for the previous exchange"
            throw if (absoluteQuery == null) IOException(reason) else TimeoutException(reason)
        }
        try {
            return exchangeLocked(request, remaining, absoluteQuery, queryDiagnostics, ownsProcess)
        } finally {
            exchangeLock.unlock()
        }
    }

    private fun exchangeLocked(
        request: EmbeddingRequest,
        remainingNanos: () -> Long,
        deadline: QueryDeadline?,
        queryDiagnostics: ((String) -> Unit)?,
        ownsProcess: Boolean,
    ): EmbeddingResponse {
        check(!closed.get()) { "Semantic process is closed" }
        val limit = System.nanoTime() + remainingNanos()
        val remaining = { maxOf(limit - System.nanoTime(), 0L) }
        val pending = submit(request, remaining, deadline, queryDiagnostics)
        val failure = try {
            val reply = pending.get(remaining(), TimeUnit.NANOSECONDS)
            deadline?.requireRemaining()
            return if (reply is EmbeddingResponse.Failed && deadline == null) {
                reply.copy(reason = failureReason(reply.reason))
            } else {
                reply
            }
        } catch (failure: TimeoutException) {
            failure
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            cause as? IOException ?: cause as? TimeoutException
                ?: IOException("Semantic process exchange failed: ${cause?.message}", failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            IOException("Semantic process exchange interrupted", failure)
        } finally {
            if (deadline != null) pending.cancel(false)
        }
        throw exchangeFailure(failure, deadline, ownsProcess)
    }

    private fun submit(
        request: EmbeddingRequest,
        remainingNanos: () -> Long,
        deadline: QueryDeadline?,
        queryDiagnostics: ((String) -> Unit)?,
    ) = exchanges.submit<EmbeddingResponse> {
        check(!closed.get()) { "Semantic process is closed" }
        deadline?.requireRemaining()
        if (deadline != null) queried.set(true)
        diagnostics.route(deadline != null, queryDiagnostics ?: indexingDiagnostics, remainingNanos)
        output.write(EmbeddingProtocol.encode(request))
        output.newLine()
        output.flush()
        EmbeddingProtocol.decodeResponse(input.readLine() ?: throw IOException("Semantic process exited"))
    }

    private fun exchangeFailure(failure: Exception, deadline: QueryDeadline?, ownsProcess: Boolean): Exception {
        val reason = if (ownsProcess) {
            runCatching { failureReason(failure.message ?: "Semantic process deadline exceeded", deadline) }
        } else {
            Result.success(failure.message.orEmpty())
        }
        reason.exceptionOrNull()?.let(failure::addSuppressed)
        return deadline?.let { failure as? TimeoutException }
            ?: IOException(reason.getOrElse { it.message.orEmpty() }, failure)
    }

    override fun close(): Unit = close(null)

    fun close(deadline: QueryDeadline?) {
        if (!closed.compareAndSet(false, true)) return
        var interrupted = false
        val remaining = {
            deadline?.remaining()?.inWholeNanoseconds ?: TimeUnit.MILLISECONDS.toNanos(WorkerDiagnostics.DRAIN_MILLIS)
        }
        val closeStream: (AutoCloseable) -> Unit = if (deadline == null) {
            { it.close() }
        } else {
            { stream -> exchanges.submit { stream.close() } }
        }
        val steps = listOf<() -> Unit>(
            { diagnostics.drain(deadline) },
            ::terminate,
            {
                try {
                    diagnostics.await(remaining, queried.get())
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            },
            { if (deadline == null) exchanges.shutdownNow() },
            { closeStream(input) },
            { closeStream(output) },
            { exchanges.shutdown() },
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
        const val DIAGNOSTIC_LINES = 16
        const val DIAGNOSTIC_CHARACTERS = 2048
        const val DIAGNOSTIC_PREFIX_CHARACTERS = 160
        const val DIAGNOSTIC_MIN_PREFIX_CHARACTERS = 64
        const val TRUNCATED = "\n[stderr truncated]"
    }
}

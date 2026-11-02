package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.serve.ServeServices
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Work that a thread of the map hands to the serve thread, and the answer that it waits for. */
internal class MapJob<T>(private val work: (ServeServices) -> T) {
    private val result = CompletableFuture<T>()

    fun run(services: ServeServices) {
        if (result.isDone) return
        runCatching { work(services) }.onSuccess(result::complete).onFailure(result::completeExceptionally)
    }

    fun cancel() {
        result.cancel(false)
    }

    fun await(timeoutMillis: Long): MapOutcome<T> = try {
        MapOutcome.Done(result.get(timeoutMillis, TimeUnit.MILLISECONDS))
    } catch (failure: ExecutionException) {
        MapOutcome.Failed(failure.cause ?: failure)
    } catch (_: TimeoutException) {
        cancel()
        MapOutcome.TimedOut
    } catch (_: CancellationException) {
        MapOutcome.TimedOut
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        cancel()
        MapOutcome.TimedOut
    }
}

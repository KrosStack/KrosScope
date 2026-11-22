package io.github.krosstack.kscope.service.semantic

import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One granted share of the machine-wide allowance; the modification time of its file is its heartbeat. */
internal class ProcessReservation(
    private val file: Path,
    private val now: () -> Long,
    heartbeatMillis: Long = HEARTBEAT_MILLIS,
) : AutoCloseable {
    private val heartbeat = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "semantic-reservation").apply { isDaemon = true }
    }

    init {
        heartbeat.scheduleWithFixedDelay(::renew, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS)
    }

    @Volatile
    private var lostReason: String? = null

    fun renew() {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(now()))
        } catch (_: NoSuchFileException) {
            lostReason = "Another process released the worker's memory reservation after its heartbeat went stale"
            heartbeat.shutdown()
        } catch (_: IOException) {
            return
        }
    }

    fun lost(): String? = lostReason

    override fun close() {
        heartbeat.shutdownNow()
        Files.deleteIfExists(file)
    }

    companion object {
        const val HEARTBEAT_MILLIS = 60_000L
        const val STALE_MILLIS = 5 * 60_000L
    }
}

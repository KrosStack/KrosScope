package io.github.krosstack.kscope.semantic.store

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

public class GenerationLease internal constructor(private val file: Path, private val now: () -> Long) : AutoCloseable {
    private var closed = false
    private var loaded: GenerationFile? = null
    public val generation: GenerationFile get() = checkNotNull(loaded)
    init {
        synchronized(users) {
            Files.createDirectories(file.parent)
            Files.write(file, byteArrayOf())
            Files.setLastModifiedTime(file, FileTime.fromMillis(now()))
            users[file] = users.getOrDefault(file, 0) + 1
        }
    }

    private val renewal = executor.scheduleAtFixedRate({ heartbeat() }, MINUTE, MINUTE, TimeUnit.MILLISECONDS)

    internal fun load(input: InputStream, expectedDigest: String): Boolean {
        loaded = GenerationFile.read(input, expectedDigest)
        return loaded != null
    }

    public fun renew() {
        synchronized(users) {
            if (!closed && Files.exists(file)) Files.setLastModifiedTime(file, FileTime.fromMillis(now()))
        }
    }

    override fun close() {
        synchronized(users) {
            if (closed) return
            closed = true
            renewal.cancel(false)
            val remaining = users.getValue(file) - 1
            if (remaining == 0) {
                users.remove(file)
                Files.deleteIfExists(file)
            } else {
                users[file] = remaining
            }
        }
    }

    private fun heartbeat() {
        try {
            renew()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        val users = mutableMapOf<Path, Int>()
        val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "embedding-leases").apply { isDaemon = true }
        }
    }
}

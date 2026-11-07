package io.github.krosstack.kscope.service.trust

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Lets one process or thread at a time read the trust file and replace it, so that no change is written over. */
internal object TrustFileLock {
    private val inProcess = ConcurrentHashMap<Path, ReentrantLock>()

    // A file lock belongs to the whole JVM: a second thread that asks for it fails instead of waiting.
    fun <T> exclusively(lockFile: Path, block: () -> T): T {
        val directory = Files.createDirectories(lockFile.toAbsolutePath().parent).toRealPath()
        val key = directory.resolve(lockFile.fileName)
        val lock = inProcess.computeIfAbsent(key) { ReentrantLock() }
        return lock.withLock {
            if (lock.holdCount > 1) return@withLock block()
            FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { block() }
            }
        }
    }

    fun <T> tryExclusively(lockFile: Path, timeoutMillis: Long, block: () -> T): T? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val directory = Files.createDirectories(lockFile.toAbsolutePath().parent).toRealPath()
        val key = directory.resolve(lockFile.fileName)
        val lock = inProcess.computeIfAbsent(key) { ReentrantLock() }
        if (!lock.tryLock(maxOf(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) return null
        try {
            return if (lock.holdCount > 1) {
                block()
            } else {
                tryFileLock(key, deadline, block)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun <T> tryFileLock(key: Path, deadline: Long, block: () -> T): T? =
        FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            var fileLock: FileLock? = null
            while (fileLock == null && System.nanoTime() < deadline) {
                fileLock = channel.tryLock()
                if (fileLock == null) {
                    val remaining = deadline - System.nanoTime()
                    TimeUnit.NANOSECONDS.sleep(remaining.coerceIn(0, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)))
                }
            }
            fileLock?.use { block() }
        }

    private const val POLL_MILLIS = 50L
}

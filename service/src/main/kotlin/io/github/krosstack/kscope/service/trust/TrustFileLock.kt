package io.github.krosstack.kscope.service.trust

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Lets one process or thread at a time read the trust file and replace it, so that no change is written over. */
internal object TrustFileLock {
    private val inProcess = ConcurrentHashMap<Path, ReentrantLock>()

    // A file lock belongs to the whole JVM: a second thread that asks for it fails instead of waiting.
    fun <T> exclusively(lockFile: Path, block: () -> T): T {
        val directory = Files.createDirectories(lockFile.toAbsolutePath().parent).toRealPath()
        val key = directory.resolve(lockFile.fileName)
        return inProcess.computeIfAbsent(key) { ReentrantLock() }.withLock {
            FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { block() }
            }
        }
    }
}

package io.github.krosstack.kscope.cli.service.home

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object ServiceFileLocks {
    private val inProcess = ConcurrentHashMap<Path, ReentrantLock>()

    // A file lock is held for the whole JVM, so threads of one process need a lock of their own.
    fun <T> exclusively(lockFile: Path, block: () -> T): T {
        val key = WorkspacePaths.real(lockFile)
        return inProcess.computeIfAbsent(key) { ReentrantLock() }.withLock {
            Files.createDirectories(key.parent)
            FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { block() }
            }
        }
    }
}

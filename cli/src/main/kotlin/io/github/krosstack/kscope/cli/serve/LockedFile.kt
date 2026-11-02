package io.github.krosstack.kscope.cli.serve

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class LockedFile private constructor(private val channel: FileChannel, private val lock: FileLock) :
    AutoCloseable {
    override fun close() {
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        fun acquire(file: Path): LockedFile {
            Files.createDirectories(file.parent)
            val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            try {
                val lock = tryLock(channel) ?: throw ServerAlreadyRunning()
                return LockedFile(channel, lock)
            } catch (failure: java.io.IOException) {
                channel.close()
                throw failure
            }
        }

        private fun tryLock(channel: FileChannel): FileLock? = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
    }
}

package io.github.krosstack.kscope.cli.service.home

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class ServiceLock private constructor(private val channel: FileChannel, private val lock: FileLock) :
    AutoCloseable {
    override fun close() {
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        const val ALREADY_RUNNING = "The service runs already."

        fun acquire(file: Path): ServiceLock {
            Files.createDirectories(file.parent)
            val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            try {
                val lock = tryLock(channel) ?: throw ServiceRefusal(ALREADY_RUNNING)
                return ServiceLock(channel, lock)
            } catch (failure: IOException) {
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

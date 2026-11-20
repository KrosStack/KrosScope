package io.github.krosstack.kscope.service.semantic

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Serializes graph scans and vector publication of one index directory across threads and processes. Callers take it
 * before the generation lock and never hold it during inference. A directory that does not exist yet holds no index
 * or generation to fence, so it is not created here (the scan that creates it also writes its ignore file); only the
 * in-process monitor protects that first scan.
 */
internal class SemanticScanGuard(directory: Path) {
    private val directory = directory.toAbsolutePath().normalize()

    fun <T> exclusively(action: () -> T): T {
        val existing = if (Files.isDirectory(directory)) directory.toRealPath() else null
        return synchronized(monitors.computeIfAbsent(existing ?: directory) { Any() }) {
            if (existing == null) action() else locked(existing, action)
        }
    }

    private fun <T> locked(real: Path, action: () -> T): T =
        FileChannel.open(real.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { action() } }

    private companion object {
        const val LOCK_FILE = "semantic-scan.lock"
        val monitors = ConcurrentHashMap<Path, Any>()
    }
}

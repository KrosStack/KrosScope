package io.github.krosstack.kscope.semantic.store

import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class GenerationAccess(
    private val directory: Path,
    private val now: () -> Long,
    private val openInput: (Path) -> InputStream = { Files.newInputStream(it) },
) {
    private val manifest = GenerationManifest(directory)

    private sealed interface Selection {
        data class Selected(val lease: GenerationLease, val input: InputStream, val name: String) : Selection
        data class Refused(val opening: GenerationOpening) : Selection
    }

    fun tryCurrent(provider: String): GenerationCurrent = try {
        tryLocked { readCurrent(provider) } ?: GenerationCurrent.Busy
    } catch (_: IOException) {
        GenerationCurrent.Unavailable("Generation manifest admission failed")
    }

    fun tryOpen(provider: String): GenerationOpening = try {
        when (val selected = tryLocked { selectForQuery(provider) }) {
            null -> GenerationOpening.Busy

            is Selection.Refused -> selected.opening

            is Selection.Selected -> {
                val (lease, input, name) = selected
                load(lease, input, name, provider)?.let { GenerationOpening.Opened(it, name) }
                    ?: GenerationOpening.Unavailable("Invalid generation")
            }
        }
    } catch (_: IOException) {
        GenerationOpening.Unavailable("Generation admission failed")
    }

    private fun readCurrent(provider: String): GenerationCurrent = when (val read = manifest.readForQuery()) {
        is GenerationManifest.Read.Unavailable -> GenerationCurrent.Unavailable(read.reason)

        is GenerationManifest.Read.Entries -> read.entries[provider]?.let { GenerationCurrent.Current(it) }
            ?: GenerationCurrent.Absent
    }

    private fun selectForQuery(provider: String): Selection = when (val current = readCurrent(provider)) {
        is GenerationCurrent.Current -> select(current.name)
            ?: Selection.Refused(GenerationOpening.Unavailable("Unreadable generation"))

        GenerationCurrent.Absent -> Selection.Refused(GenerationOpening.Absent)

        GenerationCurrent.Busy -> Selection.Refused(GenerationOpening.Busy)

        is GenerationCurrent.Unavailable -> Selection.Refused(GenerationOpening.Unavailable(current.reason))
    }

    fun open(provider: String): GenerationLease? {
        val opening = locked {
            val name = manifest.read()[provider] ?: return@locked null
            select(name)
        } ?: return null
        val (lease, input, name) = opening
        return load(lease, input, name, provider)
    }

    private fun select(name: String): Selection.Selected? {
        val lease = GenerationLease(directory.resolve("embeddings-leases/$name-${ProcessHandle.current().pid()}"), now)
        return try {
            Selection.Selected(lease, openInput(directory.resolve(name)), name)
        } catch (_: IOException) {
            lease.close()
            null
        }
    }

    private fun load(lease: GenerationLease, input: InputStream, name: String, provider: String): GenerationLease? {
        val digest = name.substringBeforeLast(".").substringAfterLast("-")
        return if (lease.load(input, digest) && lease.generation.header.provider == provider) {
            lease
        } else {
            lease.close()
            null
        }
    }

    fun <T> locked(action: () -> T): T = monitors.computeIfAbsent(directory) { ReentrantLock() }.withLock {
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve("embeddings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { action() } }
    }

    private fun <T> tryLocked(action: () -> T): T? {
        val monitor = monitors.computeIfAbsent(directory) { ReentrantLock() }
        if (!monitor.tryLock()) return null
        return try {
            Files.createDirectories(directory)
            FileChannel.open(directory.resolve("embeddings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                .use { channel ->
                    val lock = try {
                        channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    }
                    lock?.use { action() }
                }
        } finally {
            monitor.unlock()
        }
    }

    private companion object {
        val monitors = ConcurrentHashMap<Path, ReentrantLock>()
    }
}

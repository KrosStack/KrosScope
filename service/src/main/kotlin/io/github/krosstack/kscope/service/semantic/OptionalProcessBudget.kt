package io.github.krosstack.kscope.service.semantic

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Admits optional worker processes against one allowance for the whole machine. Reservations are files in [directory]
 * that live as long as their owner does; a stale heartbeat or a dead owner releases the share.
 */
internal class OptionalProcessBudget(
    directory: Path,
    private val now: () -> Long = System::currentTimeMillis,
    private val allowanceMebibytes: () -> Long = ::defaultAllowance,
    private val alive: (Long) -> Boolean = { ProcessHandle.of(it).isPresent },
) {
    private val directory = directory.toAbsolutePath().normalize()

    sealed interface Reservation {
        class Granted(val reservation: ProcessReservation) : Reservation
        data class Refused(val reason: String, val refusal: Refusal = Refusal.INSUFFICIENT_MEMORY) : Reservation
    }

    enum class Refusal { BUSY, INSUFFICIENT_MEMORY }

    fun reserve(mebibytes: Long = WORKER_MEBIBYTES): Reservation = locked { claim(mebibytes) }

    fun tryReserve(mebibytes: Long = WORKER_MEBIBYTES): Reservation {
        val monitor = monitors.computeIfAbsent(directory) { ReentrantLock() }
        if (!monitor.tryLock()) return busy()
        return try {
            Files.createDirectories(directory)
            FileChannel.open(directory.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                .use { channel -> channel.tryLock()?.use { claim(mebibytes) } ?: busy() }
        } catch (_: OverlappingFileLockException) {
            busy()
        } finally {
            monitor.unlock()
        }
    }

    private fun busy(): Reservation.Refused = Reservation.Refused("Optional process admission is busy", Refusal.BUSY)

    private fun claim(mebibytes: Long): Reservation {
        val held = live().sumOf { it.second }
        val allowance = allowanceMebibytes()
        return if (held + mebibytes > allowance) {
            Reservation.Refused("Optional processes hold $held of $allowance MiB; a worker needs $mebibytes MiB")
        } else {
            val file = directory.resolve("${ProcessHandle.current().pid()}-${System.nanoTime()}$SUFFIX")
            Files.writeString(file, mebibytes.toString())
            Reservation.Granted(ProcessReservation(file, now))
        }
    }

    private fun live(): List<Pair<Path, Long>> = Files.list(directory).use { paths ->
        paths.filter { it.fileName.toString().endsWith(SUFFIX) }.sorted().toList()
    }.mapNotNull { file ->
        val held = try {
            heldBy(file)
        } catch (_: NoSuchFileException) {
            null
        }
        if (held == null) Files.deleteIfExists(file)
        held?.let { file to it }
    }

    private fun heldBy(file: Path): Long? {
        val owner = file.fileName.toString().substringBefore('-').toLongOrNull() ?: return null
        val fresh = now() - Files.getLastModifiedTime(file).toMillis() < ProcessReservation.STALE_MILLIS
        return if (fresh && alive(owner)) Files.readString(file).trim().toLongOrNull() else null
    }

    private fun <T> locked(action: () -> T): T = monitors.computeIfAbsent(directory) { ReentrantLock() }.withLock {
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { action() } }
    }

    companion object {
        const val WORKER_MEBIBYTES = 1536L
        private const val MINIMUM_ALLOWANCE_MEBIBYTES = 1024L
        private const val MEBIBYTE = 1024L * 1024L
        private const val PHYSICAL_SHARE = 4
        private const val SUFFIX = ".reservation"
        private val monitors = ConcurrentHashMap<Path, ReentrantLock>()

        private fun defaultAllowance(): Long {
            val physical = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalMemorySize
            return maxOf(MINIMUM_ALLOWANCE_MEBIBYTES, physical / MEBIBYTE / PHYSICAL_SHARE)
        }
    }
}

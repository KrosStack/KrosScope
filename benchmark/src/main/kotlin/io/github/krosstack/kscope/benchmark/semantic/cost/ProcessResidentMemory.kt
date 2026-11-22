package io.github.krosstack.kscope.benchmark.semantic.cost

import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ProcessResidentMemory(
    private val readBytes: (List<Long>) -> Map<Long, Long?> = ::residentBytes,
    private val schedule: (() -> Unit) -> AutoCloseable = ::everySecond,
) {
    @Serializable
    data class Reading(val pid: Long, val bytes: Long?, val atNanos: Long)

    fun start(now: () -> Long): Sampler {
        val existing = children()
        return Sampler({ listOf(ProcessHandle.current().pid()) + (children() - existing) }, now)
    }

    private fun read(pids: List<Long>): Map<Long, Long?> {
        val valid = pids.filter { it > 0 }
        val bytes = if (valid.isEmpty()) emptyMap() else readBytes(valid)
        return pids.associateWith { bytes[it]?.takeIf { value -> value > 0 } }
    }

    private fun children(): List<Long> = ProcessHandle.current().descendants().use {
        it.map(ProcessHandle::pid).toList()
    }

    inner class Sampler(private val pids: () -> List<Long>, private val now: () -> Long) : AutoCloseable {
        private val readings = mutableListOf<Reading>()
        private var rounds = 0
        private var cappedAtNanos: Long? = null
        private val lifetime = schedule(::sample)

        fun snapshot(): List<Reading> = synchronized(readings) { readings.toList() }

        fun limitation(): String? = synchronized(readings) {
            cappedAtNanos?.let {
                "Resident memory sampling: MAX_SAMPLES=$MAX_SAMPLES reached at $it ns; later rounds not run."
            }
        }

        private fun sample() = synchronized(readings) {
            if (rounds >= MAX_SAMPLES) return@synchronized
            rounds++
            val at = now()
            val requested = pids().distinct().sorted()
            val bytes = read(requested)
            requested.forEach { readings += Reading(it, bytes[it], at) }
            if (rounds == MAX_SAMPLES) cappedAtNanos = at
        }

        override fun close() = lifetime.close()
    }

    companion object {
        private const val MAX_SAMPLES = 1800
        private const val KIBIBYTE = 1024L
        private const val READ_SECONDS = 2L

        private fun residentBytes(pids: List<Long>): Map<Long, Long?> = when {
            System.getProperty("os.name").startsWith("Windows") -> WindowsResidentMemory().read(pids)
            System.getProperty("os.name") == "Linux" -> pids.associateWith(::linuxResidentBytes)
            else -> pids.associateWith { null }
        }

        private fun linuxResidentBytes(pid: Long): Long? = try {
            Files.newBufferedReader(Path.of("/proc/$pid/status")).useLines { lines ->
                val value = lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.removePrefix("VmRSS:")?.trim()?.removeSuffix("kB")?.trim()?.toLongOrNull()
                value?.takeIf { it in 1..Long.MAX_VALUE / KIBIBYTE }?.times(KIBIBYTE)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }

        private fun everySecond(sample: () -> Unit): AutoCloseable {
            val executor = Executors.newSingleThreadScheduledExecutor()
            val task = executor.scheduleAtFixedRate(sample, 0, 1, TimeUnit.SECONDS)
            return AutoCloseable {
                task.cancel(false)
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(READ_SECONDS + 1, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                        check(
                            executor.awaitTermination(READ_SECONDS + 1, TimeUnit.SECONDS),
                        ) { "Resident sampler did not close." }
                    }
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }
}

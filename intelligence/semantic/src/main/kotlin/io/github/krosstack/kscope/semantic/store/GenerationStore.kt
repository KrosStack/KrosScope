package io.github.krosstack.kscope.semantic.store

import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class GenerationStore internal constructor(
    directory: Path,
    private val now: () -> Long,
    private val writeTemporary: (Path, ByteArray) -> Unit,
) {
    public constructor(directory: Path, now: () -> Long = System::currentTimeMillis) :
        this(directory, now, { path, bytes -> Files.write(path, bytes) })

    private val directory = directory.toAbsolutePath().normalize()
    private val manifest = GenerationManifest(this.directory)
    private val access = GenerationAccess(this.directory, now)

    public fun current(provider: String): String? = access.locked { manifest.read()[provider] }

    /** Reads only the manifest without waiting for publication; the selected file may be absent or invalid. */
    public fun tryCurrent(provider: String): GenerationCurrent = access.tryCurrent(provider)

    public fun tryOpen(provider: String): GenerationOpening = access.tryOpen(provider)

    public fun open(provider: String): GenerationLease? = access.open(provider)

    public fun publish(generation: GenerationFile): PublishResult = access.locked {
        PreparedGeneration.create(directory, generation, writeTemporary).use {
            publishFile(it.provider, it.name, it.temporary)
        }
    }

    public fun prepare(generation: GenerationFile): PreparedGeneration = access.locked {
        PreparedGeneration.create(directory, generation, writeTemporary)
    }

    public fun publish(prepared: PreparedGeneration, expectedGeneration: String?): PublishResult = access.locked {
        check(!prepared.closed) { "Preparation is closed" }
        require(prepared.temporary.parent == directory) { "Preparation belongs to another index" }
        if (manifest.read()[prepared.provider] != expectedGeneration || !Files.exists(prepared.temporary)) {
            PublishResult.Deferred("Generation changed during preparation")
        } else {
            publishFile(prepared.provider, prepared.name, prepared.temporary)
        }
    }

    public fun rebuild() {
        access.locked {
            entries(directory).filter {
                val name = it.fileName.toString()
                (name.startsWith("embeddings-") || name.matches(Regex("publishing-.*\\.tmp"))) &&
                    Files.isRegularFile(it)
            }
                .forEach { Files.deleteIfExists(it) }
            entries(directory.resolve("embeddings-leases")).forEach { Files.deleteIfExists(it) }
            Files.deleteIfExists(directory.resolve("embeddings-leases"))
            Files.deleteIfExists(directory.resolve(GenerationManifest.NAME))
            Files.deleteIfExists(directory.resolve("${GenerationManifest.NAME}.tmp"))
        }
    }

    private fun publishFile(provider: String, name: String, temporary: Path): PublishResult {
        val generations = entries(directory).filter {
            it.fileName.toString().matches(Regex("embeddings-${Regex.escape(provider)}-[0-9a-f]{64}\\.bin"))
        }
        val retained = generations.filter { it.fileName.toString() != name && leased(it.fileName.toString()) }
        if (retained.size >= MAX_GENERATIONS) return PublishResult.Deferred("Three generations still have live leases")
        val target = directory.resolve(name)
        val valid = validTarget(target, provider, name)
        val corrupt = Files.exists(target) && !valid
        return if (corrupt && leased(name)) {
            PublishResult.Deferred("Corrupt generation still leased")
        } else {
            if (!valid) {
                Files.deleteIfExists(target)
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            }
            manifest.write(manifest.read() + (provider to name))
            generations.filter { it != target && it !in retained }.forEach {
                try {
                    Files.deleteIfExists(it)
                } catch (_: IOException) {
                }
            }
            PublishResult.Published(name)
        }
    }

    private fun validTarget(target: Path, provider: String, name: String): Boolean = try {
        val digest = name.substringBeforeLast(".").substringAfterLast("-")
        GenerationFile.read(Files.newInputStream(target), digest)?.header?.provider == provider
    } catch (_: IOException) {
        false
    }

    private fun leased(name: String): Boolean = entries(directory.resolve("embeddings-leases")).any { lease ->
        try {
            lease.fileName.toString().startsWith("$name-") &&
                now() - Files.getLastModifiedTime(lease).toMillis() < STALE_MILLIS
        } catch (_: NoSuchFileException) {
            false
        }
    }

    private companion object {
        const val MAX_GENERATIONS = 3
        const val STALE_MILLIS = 5 * 60_000L

        fun entries(path: Path): List<Path> = if (Files.isDirectory(path)) {
            Files.list(path).use { it.sorted().toList() }
        } else {
            emptyList()
        }
    }
}

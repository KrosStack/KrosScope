package io.github.krosstack.kscope.semantic.store

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

public class GenerationStore internal constructor(
    directory: Path,
    private val now: () -> Long,
    private val writeTemporary: (Path, ByteArray) -> Unit,
) {
    public constructor(directory: Path, now: () -> Long = System::currentTimeMillis) :
        this(directory, now, { path, bytes -> Files.write(path, bytes) })

    private val directory = directory.toAbsolutePath().normalize()
    private val manifest = GenerationManifest(this.directory)

    public fun open(provider: String): GenerationLease? {
        val opening = locked {
            val name = manifest.read()[provider] ?: return@locked null
            val lease = GenerationLease(
                directory.resolve("embeddings-leases/$name-${ProcessHandle.current().pid()}"),
                now,
            )
            try {
                Triple(lease, Files.newInputStream(directory.resolve(name)), name)
            } catch (_: IOException) {
                lease.close()
                null
            }
        } ?: return null
        val (lease, input, name) = opening
        val digest = name.substringBeforeLast(".").substringAfterLast("-")
        return if (lease.load(input, digest) && lease.generation.header.provider == provider) {
            lease
        } else {
            lease.close()
            null
        }
    }

    public fun publish(generation: GenerationFile): PublishResult {
        val provider = generation.header.provider
        require(provider.matches(Regex("[A-Za-z0-9_-]+")))
        val bytes = generation.bytes()
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val name = "embeddings-$provider-$digest.bin"
        return locked {
            val temporary = Files.createTempFile(directory, "publishing-", ".tmp")
            try {
                writeTemporary(temporary, bytes)
                publishFile(provider, name, temporary)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    public fun rebuild() {
        locked {
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
        lease.fileName.toString().startsWith("$name-") && live(lease)
    }

    private fun live(lease: Path): Boolean = try {
        now() - Files.getLastModifiedTime(lease).toMillis() < STALE_MILLIS
    } catch (_: NoSuchFileException) {
        false
    }

    private fun entries(path: Path): List<Path> = if (Files.isDirectory(path)) {
        Files.list(path).use { it.sorted().toList() }
    } else {
        emptyList()
    }

    private fun <T> locked(action: () -> T): T = synchronized(monitors.computeIfAbsent(directory) { Any() }) {
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve("embeddings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { action() } }
    }

    private companion object {
        const val MAX_GENERATIONS = 3
        const val STALE_MILLIS = 5 * 60_000L
        val monitors = ConcurrentHashMap<Path, Any>()
    }
}

package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.semantic.WorkerSelfTest
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

public class ComponentInstaller(
    private val directory: ComponentDirectory,
    private val manifest: ComponentManifest = ComponentManifest.shipped(),
    private val fetch: (URI) -> InputStream = ::download,
) {
    public fun install(entry: ComponentEntry, platform: String, source: InstallSource): InstallResult {
        ComponentManifest.validate(entry)
        val files = entry.files.filter { it.platform == "any" || it.platform == platform }
        return try {
            directory.exclusively(entry.name) {
                var staging: Path? = null
                try {
                    directory.recover(entry.name)
                    when {
                        files.isEmpty() -> InstallResult.Unavailable(entry.name, "Unsupported platform")

                        source is InstallSource.Archive && !source.accepts(files) ->
                            InstallResult.Unavailable(entry.name, "An archive source requires one ZIP file")

                        else -> {
                            val target = directory.target(entry)
                            staging = Files.createTempDirectory(target.parent, "${entry.version}.installing-")
                            ComponentStaging.write(staging, entry)
                            val failure = files.firstNotNullOfOrNull { transfer(it, source, staging) }
                            val component = InstalledComponent(
                                entry.name,
                                entry.version,
                                staging,
                                entry.files.map { it.path },
                            )
                            failure ?: promote(component, target)
                        }
                    }
                } finally {
                    if (staging != null) InstalledComponent.delete(staging)
                }
            }
        } catch (failure: IOException) {
            InstallResult.Unavailable(entry.name, failure.message ?: "I/O failure")
        }
    }

    private fun transfer(file: ComponentFile, source: InstallSource, staging: Path): InstallResult? {
        val target = staging.resolve(file.path)
        if (Files.exists(target, NOFOLLOW_LINKS)) return InstallResult.UnsafeEntry(file.path, "Output collision")
        Files.createDirectories(target.parent)
        val failure = obtain(file, source, target)
        return when {
            failure != null -> failure
            file.path.endsWith(".zip") -> unpack(target, file, staging)
            else -> null
        }
    }

    private fun obtain(file: ComponentFile, source: InstallSource, target: Path): InstallResult? {
        val candidates = if (source == InstallSource.Network) file.urls else listOf("")
        var failure: InstallResult? = InstallResult.Unavailable(file.path, "No source")
        for (url in candidates) {
            failure = try {
                input(file, source, url).use { copy(it, target, file) }
            } catch (exception: IOException) {
                InstallResult.Unavailable(file.path, exception.message ?: "I/O failure")
            }
            if (failure == null) break
            Files.deleteIfExists(target)
        }
        return failure
    }

    private fun input(file: ComponentFile, source: InstallSource, url: String): InputStream = when (source) {
        InstallSource.Network -> fetch(URI(url))

        is InstallSource.Archive -> Files.newInputStream(source.path, NOFOLLOW_LINKS)

        is InstallSource.Directory -> source.path.resolve(file.path).let {
            if (!InstalledComponent.unlinked(it)) throw IOException("Offline source is a link")
            Files.newInputStream(it, NOFOLLOW_LINKS)
        }
    }

    private fun copy(input: InputStream, target: Path, file: ComponentFile): InstallResult? {
        val digest = MessageDigest.getInstance("SHA-256")
        val size = boundedCopy(input, target, file.size, digest) ?: return InstallResult.Oversize(file.path)
        val checksum = HexFormat.of().formatHex(digest.digest())
        return if (size == file.size && checksum.equals(file.sha256, ignoreCase = true)) {
            null
        } else {
            InstallResult.ChecksumMismatch(file.path)
        }
    }

    private fun boundedCopy(input: InputStream, target: Path, limit: Long, digest: MessageDigest?): Long? {
        var size = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        Files.newOutputStream(target).use { output ->
            var count = input.read(buffer)
            while (count != -1) {
                if (count > limit - size) return null
                size += count
                digest?.update(buffer, 0, count)
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return size
    }

    private fun unpack(archive: Path, file: ComponentFile, staging: Path): InstallResult? {
        var remaining = file.size * EXPANSION_LIMIT
        val result = ZipFile(archive.toFile()).use { zip ->
            var failure: InstallResult? = null
            for (entry in zip.entries()) {
                failure = if (!safeEntry(entry, staging)) {
                    InstallResult.UnsafeEntry(file.path, entry.name)
                } else {
                    val size = expand(zip, entry, staging.resolve(entry.name), remaining)
                    remaining -= size ?: 0
                    InstallResult.Oversize(file.path).takeIf { size == null }
                }
                if (failure != null) break
            }
            failure
        }
        if (result == null) Files.delete(archive)
        return result
    }

    private fun safeEntry(entry: ZipEntry, staging: Path): Boolean {
        val name = entry.name.removeSuffix("/")
        return InstalledComponent.relative(name) && !name.equals(InstalledComponent.MARKER, true) &&
            !name.equals(ComponentStaging.MARKER, true) &&
            (entry.isDirectory || !Files.exists(staging.resolve(name), NOFOLLOW_LINKS))
    }

    private fun expand(zip: ZipFile, entry: ZipEntry, target: Path, limit: Long): Long? {
        if (entry.isDirectory) {
            Files.createDirectories(target)
            return 0
        }
        Files.createDirectories(target.parent)
        return zip.getInputStream(entry).use { boundedCopy(it, target, limit, null) }
    }

    private fun promote(component: InstalledComponent, target: Path): InstallResult {
        InstalledComponent.write(component)
        val replaced = Files.exists(target, NOFOLLOW_LINKS)
        val previous = InstalledComponent.read(target)?.takeIf { it.problem == null }
        val owned = previous?.name == component.name
        if (!InstalledComponent.unlinked(target) || (replaced && !owned)) {
            throw IOException("Existing directory is linked or not marked for this component")
        }
        val backup = if (replaced) Files.createTempDirectory(target.parent, "${component.version}.replacing-") else null
        if (backup != null) {
            Files.delete(backup)
            Files.move(target, backup, ATOMIC_MOVE)
        }
        try {
            Files.move(component.directory, target, ATOMIC_MOVE)
        } catch (failure: IOException) {
            if (backup != null) Files.move(backup, target, ATOMIC_MOVE)
            throw failure
        }
        val retained = backup?.let(::cleanBackup)
        val installed = component.copy(directory = target)
        val selfTest = WorkerSelfTest(directory, manifest)
            .takeIf { installed.name in setOf("semantic", "semantic-model") }?.run(installed)
        val result = if (replaced) {
            InstallResult.Replaced(installed, retained, selfTest?.detail)
        } else {
            InstallResult.Installed(installed, selfTest?.detail)
        }
        return if (selfTest?.available == false) InstallResult.SelfTestFailed(result) else result
    }

    private fun cleanBackup(backup: Path): Path? = try {
        InstalledComponent.delete(backup)
        null
    } catch (_: IOException) {
        backup
    }

    internal companion object {
        internal const val EXPANSION_LIMIT = 8
        private val SUCCESS = 200..299

        private fun download(uri: URI): InputStream {
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
            val response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in SUCCESS) {
                response.body().close()
                throw IOException("HTTP ${response.statusCode()}")
            }
            return response.body()
        }
    }
}

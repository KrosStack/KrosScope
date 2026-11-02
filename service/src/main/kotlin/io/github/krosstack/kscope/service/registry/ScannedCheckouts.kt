package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.service.trust.MachineDirectory
import io.github.krosstack.kscope.service.trust.TrustFileLock
import io.github.krosstack.kscope.service.trust.WorkspaceKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Where the registered repositories that the last scan of each workspace indexed were checked out, by repository id,
 * kept in `scanned-checkouts.json` beside the settings of the service and keyed by the canonical path of the workspace.
 * Like the registrations it is a file of the user's machine and never read from a workspace, so an index cannot make
 * a directory readable by naming it. Every scan replaces the record of its workspace; a file that is missing or
 * cannot be read says nothing.
 */
internal class ScannedCheckouts(private val file: Path) {
    private val lockFile = file.resolveSibling(LOCK_FILE_NAME)

    fun read(workspace: Path): Map<RepositoryId, Path> = try {
        all()[WorkspaceKey.of(workspace)].orEmpty().entries.associate { RepositoryId(it.key) to Path.of(it.value) }
    } catch (_: InvalidPathException) {
        emptyMap()
    }

    /** Replaces the record of [workspace]; false when the file could not be written. */
    fun save(workspace: Path, checkouts: Map<RepositoryId, Path>): Boolean =
        read(workspace) == checkouts || write(workspace, checkouts)

    private fun write(workspace: Path, checkouts: Map<RepositoryId, Path>): Boolean = try {
        TrustFileLock.exclusively(lockFile) {
            val key = WorkspaceKey.of(workspace)
            val others = all() - key
            val kept = if (checkouts.isEmpty()) others else others + (key to textual(checkouts))
            replaceFile(KscopeJson.encodeToString(serializer, kept.toSortedMap()))
        }
        true
    } catch (_: IOException) {
        false
    }

    private fun textual(checkouts: Map<RepositoryId, Path>): Map<String, String> =
        checkouts.entries.associate { it.key.value to it.value.toString() }.toSortedMap()

    private fun replaceFile(text: String) {
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, "scanned-checkouts-", ".tmp")
        try {
            Files.writeString(temporary, text, Charsets.UTF_8)
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun all(): Map<String, Map<String, String>> = try {
        KscopeJson.decodeFromString(serializer, Files.readString(file, Charsets.UTF_8))
    } catch (_: IOException) {
        emptyMap()
    } catch (_: SerializationException) {
        emptyMap()
    }

    companion object {
        const val FILE_NAME = "scanned-checkouts.json"
        private const val LOCK_FILE_NAME = "scanned-checkouts.lock"
        private val serializer = serializer<Map<String, Map<String, String>>>()

        fun ofThisMachine(
            property: (String) -> String? = System::getProperty,
            environment: (String) -> String? = System::getenv,
        ): ScannedCheckouts = ScannedCheckouts(MachineDirectory.resolve(property, environment).resolve(FILE_NAME))
    }
}

package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.service.trust.MachineDirectory
import io.github.krosstack.kscope.service.trust.TrustFileLock
import io.github.krosstack.kscope.service.trust.WorkspaceKey
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * What the user registered with each workspace, kept in `workspaces.toml` beside the settings of the service and keyed
 * by the canonical path of the workspace, as trust is: the repositories from elsewhere, and the folders of built
 * archives that a scan may read. Nothing inside a workspace is ever read here, so a repository cannot register
 * directories of the user through a file it ships. A file that cannot be read registers nothing, and a change to it is
 * refused. The grammar is strict ([RegistryFileParser]): a key that this version does not know makes the file invalid.
 */
public class RepositoryRegistry(private val file: Path) {
    private val lockFile = file.resolveSibling(LOCK_FILE_NAME)

    /** The repositories registered with [workspace], in name order; none when the file cannot be read. */
    internal fun registered(workspace: Path): List<RegisteredRepository> =
        entryOf(workspace)?.repositories.orEmpty().sortedBy { it.name }

    /** The folders of built archives registered with [workspace], in the order they were registered. */
    internal fun artifactFolders(workspace: Path): List<Path> = entryOf(workspace)?.artifactFolders.orEmpty()

    /** Applies [change] to the repositories of [workspace] and writes them; null when the file cannot be read. */
    internal fun <T> change(
        workspace: Path,
        change: (List<RegisteredRepository>) -> Pair<List<RegisteredRepository>, T>,
    ): T? = update(workspace) { current ->
        val (repositories, result) = change(current.repositories)
        current.copy(repositories = repositories) to result
    }

    /** Applies [change] to the artifact folders of [workspace] and writes them; null when the file cannot be read. */
    internal fun <T> changeArtifactFolders(workspace: Path, change: (List<Path>) -> Pair<List<Path>, T>): T? =
        update(workspace) { current ->
            val (folders, result) = change(current.artifactFolders)
            current.copy(artifactFolders = folders) to result
        }

    internal fun isReadable(): Boolean = read() != null

    private fun entryOf(workspace: Path): RegisteredWorkspace? = read()?.get(WorkspaceKey.of(workspace))

    private fun <T> update(workspace: Path, change: (RegisteredWorkspace) -> Pair<RegisteredWorkspace, T>): T? =
        TrustFileLock.exclusively(lockFile) {
            val all = read() ?: return@exclusively null
            val key = WorkspaceKey.of(workspace)
            val current = all[key] ?: RegisteredWorkspace()
            val (changed, result) = change(current)
            if (changed != current) write(all + (key to changed))
            result
        }

    private fun read(): Map<String, RegisteredWorkspace>? = try {
        RegistryFileParser.parse(Files.readString(file, Charsets.UTF_8))
    } catch (_: NoSuchFileException) {
        emptyMap()
    } catch (_: IOException) {
        null
    }

    private fun write(all: Map<String, RegisteredWorkspace>) {
        val directory = file.toAbsolutePath().parent
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "workspaces-", ".tmp")
        try {
            Files.writeString(temporary, RegistryFileText.render(all), Charsets.UTF_8)
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    public companion object {
        public const val FILE_NAME: String = "workspaces.toml"
        private const val LOCK_FILE_NAME = "workspaces.lock"

        public fun ofThisMachine(
            property: (String) -> String? = System::getProperty,
            environment: (String) -> String? = System::getenv,
        ): RepositoryRegistry = RepositoryRegistry(MachineDirectory.resolve(property, environment).resolve(FILE_NAME))
    }
}

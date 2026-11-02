package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.service.registry.FolderOutcome.Reason
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Registers the folders of built archives that a scan of the workspace at [workspace] may read, such as a Maven local
 * repository or a Gradle cache, and unregisters them. A folder is kept by its real path when it exists, and is looked
 * in after the folders registered before it. Nothing in the workspace or in the folder changes, and nothing is built.
 * The user's shell expands `~`; it is never expanded here.
 */
public class ArtifactFolderRegistration(
    private val workspace: Path,
    private val registry: RepositoryRegistry = RepositoryRegistry.ofThisMachine(),
) {
    /** Registers [directory], which must be a directory now. */
    public fun add(directory: Path): FolderOutcome = when {
        !registry.isReadable() -> unreadable()
        !Files.isDirectory(directory) -> notADirectory(directory)
        else -> register(real(directory))
    }

    /** Registers the Maven local repository, `.m2/repository` below the `user.home` that [property] says. */
    public fun addMavenLocal(property: (String) -> String? = System::getProperty): FolderOutcome {
        val home = property(USER_HOME)?.takeIf(String::isNotBlank)
        val repository = try {
            home?.let { Path.of(it, MAVEN_DIRECTORY, MAVEN_REPOSITORY) }
        } catch (_: InvalidPathException) {
            null
        }
        return if (repository == null) {
            refused(Reason.NO_USER_HOME, "The home directory of the user is not known, so Maven local is not found.")
        } else {
            add(repository)
        }
    }

    /** Unregisters [directory], also when it is gone; the folder stays on disk. */
    public fun remove(directory: Path): FolderOutcome {
        if (!registry.isReadable()) return unreadable()
        val removed = registry.changeArtifactFolders(workspace) { current ->
            val match = current.firstOrNull { isSame(it, directory) }
            (current - listOfNotNull(match).toSet()) to match
        }
        return removed?.let { FolderOutcome.Removed(it) } ?: FolderOutcome.NotRegistered(absolute(directory))
    }

    /** The folders registered with the workspace, in the order they are looked in. */
    public fun list(): List<RegisteredFolder> =
        registry.artifactFolders(workspace).map { RegisteredFolder(it, Files.isDirectory(it)) }

    private fun register(folder: Path): FolderOutcome = registry.changeArtifactFolders(workspace) { current ->
        val same = current.firstOrNull { isSame(it, folder) }
        when (same) {
            null -> (current + listOf(folder)) to FolderOutcome.Registered(folder)
            else -> current to FolderOutcome.AlreadyRegistered(same)
        }
    } ?: unreadable()

    private fun notADirectory(directory: Path): FolderOutcome {
        val hint = if (directory.toString().startsWith(TILDE)) TILDE_HINT else ""
        return refused(Reason.NOT_A_DIRECTORY, "${absolute(directory)} is not a directory.$hint")
    }

    private fun unreadable() = refused(
        Reason.UNREADABLE_REGISTRY,
        "The list of registered folders is not valid; fix or delete ${RepositoryRegistry.FILE_NAME}, then try again.",
    )

    private fun refused(reason: Reason, message: String) = FolderOutcome.Refused(reason, message)

    private companion object {
        const val USER_HOME = "user.home"
        const val MAVEN_DIRECTORY = ".m2"
        const val MAVEN_REPOSITORY = "repository"
        const val TILDE = "~"
        const val TILDE_HINT = " A leading ~ is expanded by your shell, not by kscope."

        fun absolute(path: Path): Path = path.toAbsolutePath().normalize()

        fun real(path: Path): Path = try {
            path.toAbsolutePath().toRealPath()
        } catch (_: IOException) {
            absolute(path)
        }

        fun isSame(registered: Path, given: Path): Boolean = try {
            Files.isSameFile(registered, given)
        } catch (_: IOException) {
            registered == absolute(given)
        }
    }
}

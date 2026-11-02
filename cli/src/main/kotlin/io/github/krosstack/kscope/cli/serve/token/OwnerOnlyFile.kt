package io.github.krosstack.kscope.cli.serve.token

import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

/**
 * Creates the files that hold a server token. The token is the whole authority over the server, so a file that other
 * accounts could read is never created: when this file system cannot restrict it to the account of the process, the
 * creation fails and the server does not start.
 */
internal object OwnerOnlyFile {
    fun createTemporary(
        directory: Path,
        prefix: String,
        suffix: String,
        store: FileStore = Files.getFileStore(directory),
    ): Path = when {
        store.supportsFileAttributeView(POSIX_VIEW) -> {
            val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY))
            Files.createTempFile(directory, prefix, suffix, permissions)
        }

        store.supportsFileAttributeView(AclFileAttributeView::class.java) -> {
            val file = Files.createTempFile(directory, prefix, suffix)
            runCatching { AccountOnlyAcl.restrict(file) }.onFailure { Files.deleteIfExists(file) }.getOrThrow()
            file
        }

        else -> throw IOException(
            "The file system of $directory has neither POSIX permissions nor access control lists, so a file " +
                "that only your account can read cannot be made there. Use a workspace on another file system.",
        )
    }

    private const val POSIX_VIEW = "posix"
    private const val OWNER_ONLY = "rw-------"
}

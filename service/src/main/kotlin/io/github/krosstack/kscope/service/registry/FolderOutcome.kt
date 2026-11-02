package io.github.krosstack.kscope.service.registry

import java.nio.file.Path

/** What registering or unregistering a folder of built archives with a workspace did. */
public sealed interface FolderOutcome {
    public data class Registered(public val folder: Path) : FolderOutcome

    public data class AlreadyRegistered(public val folder: Path) : FolderOutcome

    public data class Removed(public val folder: Path) : FolderOutcome

    public data class NotRegistered(public val folder: Path) : FolderOutcome

    /** Nothing changed, for [reason], which [message] explains to a person. */
    public data class Refused(public val reason: Reason, public val message: String) : FolderOutcome

    public enum class Reason {
        NOT_A_DIRECTORY,
        NO_USER_HOME,
        UNREADABLE_REGISTRY,
    }
}

/** A registered folder and whether it is still a directory on disk. */
public data class RegisteredFolder(public val path: Path, public val present: Boolean)

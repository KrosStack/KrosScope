package io.github.krosstack.kscope.service.registry

/** What registering or unregistering a repository with a workspace did. */
public sealed interface RegistrationOutcome {
    /** [repository] is registered; [cloned] says that its checkout was cloned first. */
    public data class Registered(public val repository: RegisteredCheckout, public val cloned: Boolean) :
        RegistrationOutcome

    public data class AlreadyRegistered(public val repository: RegisteredCheckout) : RegistrationOutcome

    public data class Removed(public val repository: RegisteredCheckout) : RegistrationOutcome

    public data class NotRegistered(public val name: String) : RegistrationOutcome

    /** Nothing changed, for [reason], which [message] explains to a person. */
    public data class Refused(public val reason: Reason, public val message: String) : RegistrationOutcome

    public enum class Reason {
        NOT_A_REPOSITORY,
        WITHOUT_COMMIT,
        INSIDE_WORKSPACE,
        NAME_TAKEN,
        INVALID_NAME,
        DIRECTORY_EXISTS,
        CLONE_FAILED,
        GIT_UNAVAILABLE,
        UNREADABLE_REGISTRY,
    }
}

/** A registered repository and whether its checkout is still a directory on disk. */
public data class RegisteredState(public val repository: RegisteredCheckout, public val present: Boolean)

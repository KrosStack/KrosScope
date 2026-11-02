package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.CheckoutInspection
import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.service.registry.RegistrationOutcome.Reason
import java.nio.file.Files
import java.nio.file.Path

/**
 * Registers repositories with the workspace at [workspace] and unregisters them. A repository is a committed Git work
 * tree outside the workspace; a Git URL is cloned first with the user's Git, by default beside the workspace root.
 * Nothing in the workspace changes, and no build runs.
 */
public class RepositoryRegistration(
    workspace: Path,
    private val registry: RepositoryRegistry = RepositoryRegistry.ofThisMachine(),
    private val git: String = "git",
) {
    private val root: Path = real(workspace)

    /** Registers the checkout at [location], or clones it first when it is a Git URL. */
    public fun add(
        location: String,
        name: String? = null,
        ref: String? = null,
        directory: Path? = null,
    ): RegistrationOutcome = when {
        !registry.isReadable() -> unreadable()
        !isUrl(location) -> register(Path.of(location), name, cloned = false)
        else -> cloneAndRegister(location, name, ref, directory)
    }

    /** Unregisters the repository named [name]; its checkout stays on disk. */
    public fun remove(name: String): RegistrationOutcome {
        if (!registry.isReadable()) return unreadable()
        val removed = registry.change(root) { current ->
            val match = current.firstOrNull { it.name == name }
            (current - listOfNotNull(match).toSet()) to match
        }
        return removed?.let { RegistrationOutcome.Removed(it.checkout()) } ?: RegistrationOutcome.NotRegistered(name)
    }

    /** The repositories registered with the workspace, and whether each checkout still exists. */
    public fun list(): List<RegisteredState> =
        registry.registered(root).map { RegisteredState(it.checkout(), Files.isDirectory(it.root)) }

    private fun cloneAndRegister(location: String, name: String?, ref: String?, directory: Path?): RegistrationOutcome {
        val names = names()
        val chosen = name ?: names.unique(RepositoryNames.ofLocation(location))
        if (!names.isValid(chosen)) return invalidName(chosen)
        val target = (directory ?: root.resolveSibling(chosen)).toAbsolutePath().normalize()
        return when (val clone = RepositoryClone(git).clone(location, ref, target)) {
            is RepositoryClone.Result.Cloned -> register(target, chosen, cloned = true)
            is RepositoryClone.Result.Failed -> clone.outcome
        }
    }

    private fun register(location: Path, name: String?, cloned: Boolean): RegistrationOutcome =
        when (val inspection = CheckoutInspection.of(location, git)) {
            is CheckoutInspection.Committed -> registerCheckout(real(inspection.root), name, cloned)

            is CheckoutInspection.WithoutCommit ->
                refused(Reason.WITHOUT_COMMIT, "${inspection.root} has no commit yet.")

            CheckoutInspection.NotARepository -> refused(Reason.NOT_A_REPOSITORY, "$location is not a Git work tree.")

            is CheckoutInspection.GitUnavailable -> refused(Reason.GIT_UNAVAILABLE, inspection.reason)
        }

    private fun registerCheckout(checkout: Path, name: String?, cloned: Boolean): RegistrationOutcome {
        val names = names()
        val chosen = name ?: names.unique(checkout.fileName.toString())
        return when {
            checkout.startsWith(root) || root.startsWith(checkout) ->
                refused(Reason.INSIDE_WORKSPACE, "$checkout is already part of the workspace at $root.")

            !names.isValid(chosen) -> invalidName(chosen)

            else -> {
                val repository = RegisteredRepository(chosen, checkout)
                registry.change(root) { current -> added(current, repository, cloned) } ?: unreadable()
            }
        }
    }

    private fun added(
        current: List<RegisteredRepository>,
        repository: RegisteredRepository,
        cloned: Boolean,
    ): Pair<List<RegisteredRepository>, RegistrationOutcome> {
        val same = current.firstOrNull { real(it.root) == repository.root }
        val taken = current.any { it.name == repository.name } || Files.exists(root.resolve(repository.name))
        return when {
            same != null -> current to RegistrationOutcome.AlreadyRegistered(same.checkout())
            taken -> current to refused(Reason.NAME_TAKEN, "${repository.name} is already a name in the workspace.")
            else -> (current + repository) to RegistrationOutcome.Registered(repository.checkout(), cloned)
        }
    }

    private fun names() = RepositoryNames(root, registry.registered(root).map { it.name }.toSet())

    private fun invalidName(name: String) = refused(
        Reason.INVALID_NAME,
        "$name is not a valid name: use letters, digits, dots, hyphens and underscores, not starting with a dot.",
    )

    private fun unreadable() = refused(
        Reason.UNREADABLE_REGISTRY,
        "The list of registered repositories is not valid; fix or delete " +
            "${RepositoryRegistry.FILE_NAME}, then try again.",
    )

    private fun refused(reason: Reason, message: String) = RegistrationOutcome.Refused(reason, message)

    private companion object {
        private val URL = Regex("^[A-Za-z][A-Za-z0-9+.-]*://.+|^[^/\\\\\\s]+@[^:/\\s]+:.+")

        fun isUrl(location: String): Boolean = URL.matches(location)

        fun RegisteredRepository.checkout() = RegisteredCheckout(name, root)

        fun real(path: Path): Path = try {
            path.toAbsolutePath().toRealPath()
        } catch (_: java.io.IOException) {
            path.toAbsolutePath().normalize()
        }
    }
}

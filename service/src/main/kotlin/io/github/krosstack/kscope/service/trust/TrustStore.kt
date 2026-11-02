package io.github.krosstack.kscope.service.trust

import org.tomlj.Toml
import org.tomlj.TomlArray
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The workspaces that the user trusted to run their own build on this machine, kept in `trust.toml` beside the
 * settings of the service. Trust is keyed by the canonical path of the workspace. A workspace cannot grant it to
 * itself: nothing inside a workspace is ever read here. A file that cannot be read trusts nothing.
 */
public class TrustStore(private val file: Path) {
    private val lockFile = file.resolveSibling(LOCK_FILE_NAME)

    public fun isTrusted(workspace: Path): Boolean = workspaces()?.contains(WorkspaceKey.of(workspace)) == true

    /** Trusts [workspace]; returns false when it was trusted already. */
    public fun grant(workspace: Path): Boolean {
        val key = WorkspaceKey.of(workspace)
        return replace { it + key }
    }

    /** Stops trusting [workspace]; returns false when it was not trusted. */
    public fun revoke(workspace: Path): Boolean {
        val key = WorkspaceKey.of(workspace)
        return replace { it - key }
    }

    private fun replace(change: (Set<String>) -> Set<String>): Boolean = TrustFileLock.exclusively(lockFile) {
        val current = requireReadable()
        val changed = change(current)
        (changed != current).also { if (it) write(changed) }
    }

    private fun requireReadable(): Set<String> =
        workspaces() ?: throw TrustStoreException("$file is not valid; fix or delete it, then try again.")

    private fun workspaces(): Set<String>? = try {
        parse(Files.readString(file, Charsets.UTF_8))
    } catch (_: NoSuchFileException) {
        emptySet()
    } catch (_: IOException) {
        null
    }

    private fun parse(text: String): Set<String>? {
        val toml = Toml.parse(text)
        val array = toml.get(listOf(TrustFileText.KEY)) as? TomlArray
        val entries = array?.let { (0 until it.size()).map(it::get) }.orEmpty()
        return when {
            toml.hasErrors() || toml.keySet().any { it != TrustFileText.KEY } -> null
            array == null && toml.keySet().isNotEmpty() -> null
            entries.any { it !is String } -> null
            else -> entries.filterIsInstance<String>().toSet()
        }
    }

    private fun write(workspaces: Collection<String>) {
        val directory = file.toAbsolutePath().parent
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "trust-", ".tmp")
        try {
            Files.writeString(temporary, TrustFileText.render(workspaces), Charsets.UTF_8)
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    public companion object {
        public const val FILE_NAME: String = "trust.toml"
        private const val LOCK_FILE_NAME = "trust.lock"

        public fun ofThisMachine(
            property: (String) -> String? = System::getProperty,
            environment: (String) -> String? = System::getenv,
        ): TrustStore = TrustStore(MachineDirectory.resolve(property, environment).resolve(FILE_NAME))
    }
}

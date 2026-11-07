package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.semantic.WorkerSelfTest
import io.github.krosstack.kscope.service.trust.TrustFileLock
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

public class ComponentDirectory(public val root: Path) {
    public fun installed(name: String): InstalledComponent? = installed().firstOrNull { it.name == name }

    public fun installed(): List<InstalledComponent> {
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || !InstalledComponent.unlinked(root)) return emptyList()
        return Files.walk(root, 2).use { paths ->
            paths.filter { it.nameCount == root.nameCount + 2 }.sorted().map { path ->
                InstalledComponent.read(path)?.let { withSelfTestProblem(it) }
            }.toList().filterNotNull()
        }
    }

    internal fun withSelfTestProblem(
        component: InstalledComponent,
        read: (Path) -> String = {
            Files.newInputStream(it, NOFOLLOW_LINKS).bufferedReader().use { reader -> reader.readText() }
        },
    ): InstalledComponent {
        val marker = component.directory.resolve(WorkerSelfTest.PROBLEM_FILE)
        return if (component.problem == null && Files.isRegularFile(marker, NOFOLLOW_LINKS)) {
            val problem = try {
                read(marker)
            } catch (_: NoSuchFileException) {
                null
            } catch (failure: IOException) {
                "Cannot read self-test problem: ${failure.message}"
            }
            component.copy(problem = problem)
        } else {
            component
        }
    }

    public fun remove(name: String): RemoveResult {
        if (!InstalledComponent.validSegment(name)) return RemoveResult.Refused("Invalid component name")
        val parent = root.resolve(name)
        return when {
            !Files.exists(parent, NOFOLLOW_LINKS) -> RemoveResult.NotInstalled

            !InstalledComponent.unlinked(parent) -> RemoveResult.Refused("Component directory is a link: $parent")

            !Files.isDirectory(parent, NOFOLLOW_LINKS) -> RemoveResult.Refused("Component path is not a directory")

            else -> try {
                exclusively(name) {
                    recover(name)
                    removeMarked(parent, name)
                }
            } catch (failure: IOException) {
                RemoveResult.Refused(failure.message ?: "I/O failure: $parent")
            }
        }
    }

    internal fun <T> exclusively(name: String, action: () -> T): T {
        val lockFile = lockFile(name)
        return if (name == "semantic-model") {
            exclusively("semantic") { TrustFileLock.exclusively(lockFile, action) }
        } else {
            TrustFileLock.exclusively(lockFile, action)
        }
    }

    internal fun <T> trySemanticLock(timeoutMillis: Long, action: () -> T): T? =
        TrustFileLock.tryExclusively(lockFile("semantic"), timeoutMillis, action)

    private fun lockFile(name: String): Path {
        val lockFile = root.resolve(name).resolve(LOCK_FILE)
        if (!InstalledComponent.unlinked(lockFile)) throw IOException("Component lock path is a link")
        if (Files.exists(lockFile, NOFOLLOW_LINKS) && !Files.isRegularFile(lockFile, NOFOLLOW_LINKS)) {
            throw IOException("Component lock path is not a regular file")
        }
        return lockFile
    }

    internal fun recover(name: String) {
        val parent = root.resolve(name)
        if (!Files.exists(parent, NOFOLLOW_LINKS)) return
        if (!InstalledComponent.unlinked(parent)) throw IOException("Component directory is a link: $parent")
        val paths = Files.list(parent).use { it.sorted().toList() }
        paths.filter(ComponentStaging::owns).forEach { InstalledComponent.delete(it) }
        paths.filter { it.fileName.toString().contains(".replacing-") }.forEach { backup ->
            val component = InstalledComponent.read(backup)
            if (component != null && component.problem == null) {
                val target = parent.resolve(component.version)
                if (!Files.exists(target, NOFOLLOW_LINKS)) Files.move(backup, target, ATOMIC_MOVE)
            }
        }
    }

    internal fun target(entry: ComponentEntry): Path {
        val target = root.resolve(entry.name).resolve(entry.version)
        if (!InstalledComponent.unlinked(target)) throw IOException("Component directory is a link")
        if (Files.exists(target, NOFOLLOW_LINKS) && !marked(target, entry.name)) {
            throw IOException("Existing directory is not marked for this component")
        }
        Files.createDirectories(target.parent)
        return target
    }

    private fun removeMarked(parent: Path, name: String): RemoveResult {
        val paths = Files.list(parent).use { paths ->
            paths.filter { it.fileName.toString() != LOCK_FILE }.sorted().toList()
        }
        val boundary = parent.toRealPath()
        paths.forEach {
            if (!it.toRealPath().startsWith(boundary)) {
                throw IOException("Path outside component directory: $it")
            }
        }
        if (paths.isEmpty() || paths.any { !marked(it, name) }) {
            return RemoveResult.Refused("Component directory is not marked for $name")
        }
        paths.forEach { InstalledComponent.delete(it, boundary) }
        return RemoveResult.Removed
    }

    private fun marked(path: Path, name: String): Boolean = InstalledComponent.read(path)?.let {
        it.name == name && it.problem == null
    } == true

    public companion object {
        internal const val LOCK_FILE = ".lock"

        public fun resolve(property: (String) -> String?, environment: (String) -> String?): ComponentDirectory {
            val configured = listOf(property("kscope.components.home"), environment("KSCOPE_COMPONENTS_HOME"))
                .firstOrNull { !it.isNullOrBlank() }
            val root = configured?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".kscope", "components")
            return ComponentDirectory(root.toAbsolutePath().normalize())
        }
    }
}

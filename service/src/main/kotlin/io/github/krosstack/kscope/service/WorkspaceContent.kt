package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the files of the index from the checkout of their repository and refuses any that resolves outside it. A
 * repository registered from elsewhere or recorded by the last scan has its directory in [external]; the real
 * directory is then the boundary. Any other repository is read from its directory in [local], or from [root], and
 * both its directory and its files must stay below the real path of [root]. The real path of each checkout and of
 * each directory is looked up once, because resolving every component of a path costs a directory lookup each on
 * Windows.
 */
internal class WorkspaceContent(
    private val root: Path,
    private val known: Set<ArtifactRef>,
    private val local: Map<RepositoryId, Path> = emptyMap(),
    private val external: Map<RepositoryId, Path> = emptyMap(),
) : WorkspaceFiles {
    private val realRoot by lazy { root.toRealPath() }
    private val realCheckouts = ConcurrentHashMap<RepositoryId, Path>()
    private val realDirectories = ConcurrentHashMap<Path, Path>()

    override fun read(ref: ArtifactRef): ByteArray = Files.readAllBytes(absolutePath(ref))

    override fun absolutePath(ref: ArtifactRef): Path {
        if (ref !in known) throw IOException("The artifact is not present in this workspace index")
        val checkout = realCheckouts.computeIfAbsent(ref.repository, ::realCheckout)
        val resolved = realPath(checkout.resolve(ref.path.value).normalize())
        if (!resolved.startsWith(checkout)) throw IOException("The artifact resolves outside its repository")
        return resolved
    }

    private fun realCheckout(repository: RepositoryId): Path {
        external[repository]?.let { return it.toRealPath() }
        val checkout = (local[repository] ?: root).toRealPath()
        if (!checkout.startsWith(realRoot)) throw IOException("The repository directory resolves outside the workspace")
        return checkout
    }

    private fun realPath(candidate: Path): Path {
        val parent = candidate.parent ?: return candidate.toRealPath()
        val directory = realDirectories.computeIfAbsent(parent) { it.toRealPath() }
        val file = directory.resolve(candidate.fileName)
        return if (isPlainFile(file)) file else file.toRealPath()
    }

    private fun isPlainFile(file: Path): Boolean = try {
        !Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isSymbolicLink
    } catch (_: IOException) {
        false
    }
}

public fun KrosScope.readWorkspaceFiles(): WorkspaceFiles {
    val repositories = graph().structure().repositories
    val external = externalCheckouts.locate(repositories)
    val local = repositories.filter { it.id !in external }
        .associate { it.id to workspaceRoot.resolve(it.directory.value) }
    return WorkspaceContent(workspaceRoot, graph().artifacts().map { it.ref }.toSet(), local, external)
}

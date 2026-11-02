package io.github.krosstack.kscope.benchmark.agent.workspace

import io.github.krosstack.kscope.benchmark.agent.Fingerprint
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class WorkspaceFactory(private val git: GitCli = GitCli()) {
    fun create(source: Path, target: Path): Workspace {
        val prefix = checkedPrefix(source)
        val checkout = target.resolveSibling("${target.fileName}.checkout")
        val listing = Files.createTempFile("kscope-listing", ".txt")
        try {
            Files.writeString(listing, git.output(source, "ls-files", "-z"))
            Files.createDirectories(checkout)
            val destination = "--prefix=${checkout.toString().replace('\\', '/')}/"
            git.output(source, "checkout-index", "-z", "--stdin", "-f", destination, input = listing)
            Files.move(checkout.resolve(prefix), target, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(listing)
            FileTrees.deleteQuietly(checkout)
        }
        return Workspace(target.toRealPath(), git).also(::commitBase)
    }

    fun baseFingerprint(source: Path): String =
        Fingerprint.of(sequenceOf(git.output(source, "ls-files", "-s", "-z").toByteArray(Charsets.UTF_8)))

    private fun checkedPrefix(source: Path): String {
        val result = git.run(source, "rev-parse", "--show-prefix")
        check(result.exitCode == 0) { "The workspace must lie inside a Git work tree; the index is its base state." }
        return result.stdout.trim().trimEnd('/')
    }

    private fun commitBase(workspace: Workspace) {
        git.output(workspace.root, "init", "-q")
        git.requireRoot(workspace.root)
        git.output(workspace.root, "add", "-A")
        git.commit(workspace.root, "Base state")
    }
}

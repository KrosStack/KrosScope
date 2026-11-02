package io.github.krosstack.kscope.service.map.tree

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.service.map.wireName

/**
 * Hangs the files of one module or source set under it, in directories. The directories start at the deepest one
 * that all the files share, which drops the source root, and a directory that holds nothing but one directory is
 * joined with it into one node, labelled `io/github/foo`.
 */
internal class FileTree(private val locations: WorkspaceLocations, private val commits: Map<ArtifactRef, Int>) {
    val files = HashMap<ArtifactRef, TreeNode>()

    fun attach(container: TreeNode, artifacts: List<Artifact>) {
        val shared = artifacts.map(::directoriesOf).reduce(::commonPrefix).size
        val trie = DirectoryTrie()
        artifacts.forEach { trie.add(it, shared) }
        attach(container, trie)
    }

    private fun attach(parent: TreeNode, trie: DirectoryTrie) {
        trie.files.forEach { files[it.ref] = parent.child(fileId(it), "file", fileName(it), factsOf(it)) }
        trie.directories.forEach { (name, directory) ->
            val (label, joined) = joinedChain(name, directory)
            attach(parent.child("d:${parent.id}/$label", "directory", label), joined)
        }
    }

    private tailrec fun joinedChain(label: String, directory: DirectoryTrie): Pair<String, DirectoryTrie> {
        val only = directory.directories.entries.singleOrNull()
        if (directory.files.isNotEmpty() || only == null) return label to directory
        return joinedChain("$label/${only.key}", only.value)
    }

    private fun factsOf(artifact: Artifact) = NodeFacts(
        lines = artifact.lineCount,
        artifactType = artifact.type.id,
        category = artifact.type.category.wireName(),
        generated = artifact.generated != null,
        hash = artifact.contentHash.value.take(HASH_LENGTH),
        commits = commits[artifact.ref] ?: 0,
        path = locations.pathOf(artifact.ref),
    )

    private fun fileId(artifact: Artifact) = "f:${artifact.ref.repository.value}//${artifact.ref.path.value}"

    private fun fileName(artifact: Artifact) = artifact.ref.path.value.substringAfterLast('/')

    private companion object {
        const val HASH_LENGTH = 12
    }
}

private class DirectoryTrie {
    val directories = sortedMapOf<String, DirectoryTrie>()
    val files = mutableListOf<Artifact>()

    fun add(artifact: Artifact, skipped: Int) {
        var node = this
        directoriesOf(artifact).drop(skipped).forEach { node = node.directories.getOrPut(it) { DirectoryTrie() } }
        node.files.add(artifact)
    }
}

private fun directoriesOf(artifact: Artifact): List<String> = artifact.ref.path.value.split('/').dropLast(1)

private fun commonPrefix(first: List<String>, second: List<String>): List<String> =
    first.zip(second).takeWhile { (left, right) -> left == right }.map { it.first }

package io.github.krosstack.kscope.store.codec

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor

internal object NodeRefs {
    private const val SYMBOL = "symbol"
    private const val ARTIFACT = "artifact"
    private const val MODULE = "module"
    private const val SOURCE_SET = "source-set"
    private const val EXTERNAL = "external"
    private const val REPOSITORY_SEPARATOR = '\u001F'

    fun kindOf(node: NodeRef): String = when (node) {
        is NodeRef.OfSymbol -> SYMBOL
        is NodeRef.OfArtifact -> ARTIFACT
        is NodeRef.OfModule -> MODULE
        is NodeRef.OfSourceSet -> SOURCE_SET
        is NodeRef.OfExternal -> EXTERNAL
    }

    fun keyOf(node: NodeRef): String = when (node) {
        is NodeRef.OfSymbol -> node.id.value
        is NodeRef.OfArtifact -> artifactKey(node.ref)
        is NodeRef.OfModule -> node.id.value
        is NodeRef.OfSourceSet -> node.id.value
        is NodeRef.OfExternal -> node.coordinate
    }

    fun decode(kind: String, key: String): NodeRef = when (kind) {
        SYMBOL -> NodeRef.OfSymbol(SymbolId(key))
        ARTIFACT -> NodeRef.OfArtifact(artifactRef(key))
        MODULE -> NodeRef.OfModule(ModuleId(key))
        SOURCE_SET -> NodeRef.OfSourceSet(SourceSetId(key))
        EXTERNAL -> NodeRef.OfExternal(key)
        else -> error("Unknown stored node kind '$kind'")
    }

    private fun artifactKey(ref: ArtifactRef): String {
        require(REPOSITORY_SEPARATOR !in ref.repository.value) {
            "A repository id must not contain the unit separator character"
        }
        return ref.repository.value + REPOSITORY_SEPARATOR + ref.path.value
    }

    private fun artifactRef(key: String): ArtifactRef {
        val separator = key.indexOf(REPOSITORY_SEPARATOR)
        check(separator >= 0) { "Malformed stored artifact reference" }
        return ArtifactRef(RepositoryId(key.substring(0, separator)), RepoPath(key.substring(separator + 1)))
    }
}

internal fun nodeRefColumns(prefix: String, required: Boolean): List<String> {
    val constraint = if (required) " NOT NULL" else ""
    return listOf("${prefix}_kind TEXT$constraint", "${prefix}_key TEXT$constraint")
}

internal fun Binder.nodeRef(node: NodeRef) {
    text(NodeRefs.kindOf(node))
    text(NodeRefs.keyOf(node))
}

internal fun Binder.nodeRefOrNull(node: NodeRef?) {
    textOrNull(node?.let(NodeRefs::kindOf))
    textOrNull(node?.let(NodeRefs::keyOf))
}

internal fun Cursor.nodeRef(): NodeRef = NodeRefs.decode(text(), text())

internal fun Cursor.nodeRefOrNull(): NodeRef? {
    val kind = textOrNull()
    val key = textOrNull()
    return kind?.let { NodeRefs.decode(it, checkNotNull(key)) }
}

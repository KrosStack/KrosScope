package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.service.graph.PointLookups
import kotlinx.serialization.Serializable

@Serializable
public data class NodeResolution(public val requested: String, public val candidates: List<String>)

public fun KrosScope.resolveNode(query: String): NodeResolution {
    memory.requireIndexed()
    val lookups = PointLookups(graph())
    val split = query.lastIndexOf(':')
    val line = query.substring(split + 1).toIntOrNull()
    val path = if (split >= 0) query.substring(0, split) else query
    val isLocation = line != null && line > 0 && lookups.artifactsAt(path).isNotEmpty()
    val matches = if (isLocation) {
        symbolsAtLine(lookups.declaredAtPath(path), checkNotNull(line))
    } else {
        lookups.symbolsNamed(query)
    }
    val candidates = matches.map { it.id.value }.distinct().sorted()
    if (candidates.isEmpty()) requireWorkspacePath(lookups, path)
    return NodeResolution(query, candidates.ifEmpty { listOf(query) })
}

private fun symbolsAtLine(declared: List<Symbol>, line: Int): List<Symbol> {
    val containing = declared.filter { symbol ->
        val declaration = checkNotNull(symbol.declaration)
        line >= declaration.range.startLine &&
            (line < declaration.range.endLine || (line == declaration.range.endLine && declaration.range.endColumn > 1))
    }
    val shortest = containing.minOfOrNull {
        checkNotNull(it.declaration).range.let { range -> range.endLine - range.startLine }
    }
    return containing.filter {
        checkNotNull(it.declaration).range.let { range -> range.endLine - range.startLine == shortest }
    }
}

private fun requireWorkspacePath(lookups: PointLookups, path: String) {
    val inside = lookups.artifactsWithoutDirectory(path)
    if (inside.isEmpty()) return
    val names = inside.joinToString(", ") { lookups.locations.pathOf(it.ref) }
    throw WorkspacePathException("$path does not name a file of this workspace. Name it with its directory: $names.")
}

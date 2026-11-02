package io.github.krosstack.kscope.store.codec

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor

internal fun locationColumns(prefix: String): List<String> = listOf(
    "${prefix}_repository TEXT",
    "${prefix}_path TEXT",
    "${prefix}_start_line INTEGER",
    "${prefix}_start_column INTEGER",
    "${prefix}_end_line INTEGER",
    "${prefix}_end_column INTEGER",
)

internal fun Binder.location(location: SourceLocation?) {
    textOrNull(location?.artifact?.repository?.value)
    textOrNull(location?.artifact?.path?.value)
    intOrNull(location?.range?.startLine)
    intOrNull(location?.range?.startColumn)
    intOrNull(location?.range?.endLine)
    intOrNull(location?.range?.endColumn)
}

internal fun Cursor.locationOrNull(): SourceLocation? {
    val repository = textOrNull()
    val path = textOrNull()
    val startLine = intOrNull()
    val startColumn = intOrNull()
    val endLine = intOrNull()
    val endColumn = intOrNull()
    if (repository == null) return null
    val artifact = ArtifactRef(RepositoryId(repository), RepoPath(checkNotNull(path)))
    return SourceLocation(
        artifact,
        TextRange(checkNotNull(startLine), checkNotNull(startColumn), checkNotNull(endLine), checkNotNull(endColumn)),
    )
}

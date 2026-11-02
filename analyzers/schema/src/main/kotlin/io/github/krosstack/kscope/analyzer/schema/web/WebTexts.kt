package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

private const val MAX_SETTINGS_BYTES = 1L shl 20

internal fun WorkspaceFiles.textOf(artifact: Artifact): String? = try {
    read(artifact.ref).toString(Charsets.UTF_8)
} catch (_: IOException) {
    null
}

internal fun WorkspaceFiles.settingsTextOf(artifact: Artifact): String? =
    if (artifact.sizeBytes <= MAX_SETTINGS_BYTES) textOf(artifact) else null

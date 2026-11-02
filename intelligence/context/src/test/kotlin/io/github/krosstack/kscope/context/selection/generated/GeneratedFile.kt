package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol

internal enum class FileKind { SOURCE, TEST }

internal data class FilePlan(
    val repository: String,
    val path: String,
    val module: ModuleId?,
    val kind: FileKind,
    val twin: Int = 0,
) {
    val sourceSet: SourceSetId? get() = module?.let { sourceSetOf(repository, it) }
}

internal class GeneratedFile(val artifact: Artifact, val text: String, val symbols: List<Symbol>, val kind: FileKind)

internal class GeneratedSurface(
    val artifact: Artifact,
    val text: String,
    val symbol: Symbol,
    val generation: Relationship,
)

internal fun sourceSetOf(repository: String, module: ModuleId): SourceSetId = SourceSetId("${module.value}@$repository")

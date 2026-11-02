package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSetId

/**
 * A dependency of one module on another as the analysis reads it: the source set it is scoped to, none when it holds
 * for all of them, the Gradle configuration that decides whether the target is exposed to the consumers, and whether
 * the build model is not sure of it, which makes the references the analysis resolves across it heuristic.
 */
internal data class ModuleDependency(
    val from: ModuleId,
    val to: ModuleId,
    val sourceSet: SourceSetId?,
    val configuration: String?,
    val heuristic: Boolean,
) {
    val fingerprintParts: List<String>
        get() = listOf(from.value, to.value, sourceSet?.value.orEmpty(), configuration.orEmpty()) +
            listOfNotNull(HEURISTIC.takeIf { heuristic })

    private companion object {
        const val HEURISTIC = "heuristic"
    }
}

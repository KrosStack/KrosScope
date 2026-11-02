package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.analyzer.android.ResourceLocations
import io.github.krosstack.kscope.analyzer.android.resources.ResourceDefinition
import io.github.krosstack.kscope.analyzer.android.resources.ResourceFileReader
import io.github.krosstack.kscope.analyzer.android.resources.ResourceSymbols
import io.github.krosstack.kscope.analyzer.android.resources.symbolName
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

/** A resource definition together with the module and source set whose file declares it. */
internal class OwnedDefinition(val module: Module, val sourceSet: SourceSet, val definition: ResourceDefinition) {
    val symbolId: SymbolId = ResourceSymbols.idOf(module.id, sourceSet.symbolName(), definition.key)
}

/** The resources that the main source sets of each module define, read from the resource files. */
internal class ModuleResources(private val reader: ResourceFileReader, private val files: WorkspaceFiles) {
    private val resourceTypes = AndroidArtifactTypes.resources.map { it.id }.toSet()

    fun definitionsByModule(artifacts: List<Artifact>, model: BuildModel): Map<ModuleId, List<OwnedDefinition>> {
        val sourceSets = model.sourceSets.associateBy { it.id }
        val modules = model.modules.associateBy { it.id }
        return artifacts.filter { it.type.id in resourceTypes }
            .sortedBy { it.ref.path.value }
            .flatMap { artifact ->
                val sourceSet = artifact.sourceSet?.let(sourceSets::get)
                val module = sourceSet?.let { modules[it.module] }
                if (sourceSet == null || module == null || sourceSet.role != SourceSetRole.MAIN) {
                    emptyList()
                } else {
                    definitionsOf(artifact).map { OwnedDefinition(module, sourceSet, it) }
                }
            }
            .groupBy { it.module.id }
    }

    private fun definitionsOf(artifact: Artifact): List<ResourceDefinition> {
        val location = ResourceLocations.of(artifact.ref.path.value) ?: return emptyList()
        return try {
            reader.read(location, files.read(artifact.ref), artifact.lineCount).definitions
        } catch (_: IOException) {
            emptyList()
        }
    }
}

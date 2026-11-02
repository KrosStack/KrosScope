package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.hashing.digestOf
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.SyntheticSource

/** Digests of the model entities, synthetic sources and classpaths with the state of their entries. */
internal class ScopeDigests(
    synthetic: List<SyntheticSource>,
    private val classpaths: Map<SourceSetId, List<String>> = emptyMap(),
    private val classpathContent: ClasspathContent = ClasspathContent(),
) {
    private val definitions = HashMap<SourceSetId, String>()
    private val moduleDefinitions = HashMap<ModuleId, String>()
    private var digestedModel: BuildModel? = null
    private var modelDigest = ""
    private val classpathDigests = HashMap<SourceSetId, String>()

    private val syntheticBySourceSet: Map<SourceSetId, String> =
        synthetic.groupBy { it.sourceSet }.mapValues { (_, sources) -> digestOfSources(sources) }

    fun definition(sourceSet: SourceSet): String = definitions.getOrPut(sourceSet.id) {
        digestOf(listOf(KscopeJson.encodeToString(SourceSet.serializer(), sourceSet)))
    }

    fun module(module: Module): String = moduleDefinitions.getOrPut(module.id) {
        digestOf(listOf(KscopeJson.encodeToString(Module.serializer(), module)))
    }

    fun model(model: BuildModel): String {
        if (digestedModel !== model) {
            modelDigest = digestOf(listOf(KscopeJson.encodeToString(BuildModel.serializer(), model)))
            digestedModel = model
        }
        return modelDigest
    }

    fun synthetic(id: SourceSetId): String = syntheticBySourceSet[id].orEmpty()

    fun classpath(id: SourceSetId): String = classpathDigests.getOrPut(id) {
        classpaths[id]?.let { entries -> digestOf(entries.flatMap { listOf(it, classpathContent.of(it)) }) }.orEmpty()
    }

    private fun digestOfSources(sources: List<SyntheticSource>): String = digestOf(
        sources.sortedBy { it.virtualPath }.flatMap { listOf(it.virtualPath, it.language, it.generator, it.text) },
    )
}

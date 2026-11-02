package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.BuildModelCodec
import io.github.krosstack.kscope.engine.build.CoordinateLinks
import io.github.krosstack.kscope.engine.build.CoordinateResolution
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ProducerRef

/**
 * Links the modules of a workspace of several repositories by the coordinates they publish and depend on, and stores
 * the links with their diagnostics. Every link is added to the model that analysis sees, whatever version the consumer
 * pins: the source in the workspace is the closest thing there is to what the consumer compiles against, and the
 * links to other versions are heuristic. A workspace of one repository has no such links and no such unit.
 */
internal class CoordinateStage(private val context: ScanContext, private val session: UpdateSession) {
    fun run(merged: ResolvedBuildModel): ResolvedBuildModel {
        if (context.repositories.size < 2) return merged
        val links = CoordinateResolution(context.repositories.map { it.repository }, merged.model).resolve()
        store(links)
        val model = merged.model.copy(dependencies = merged.model.dependencies + links.relationships)
        return ResolvedBuildModel(BuildModelCodec.canonical(model), merged.classpaths)
    }

    private fun store(links: CoordinateLinks) {
        val facts = links.relationships.map { Fact.OfRelationship(it) } +
            links.diagnostics.map { Fact.OfDiagnostic(it) }
        WorkspaceFacts(session).store(producer, facts)
    }

    companion object {
        val producer = ProducerRef("kscope.engine.coordinates", "1")
    }
}

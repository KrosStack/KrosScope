package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution

/** Builds the dependency between a consumer and the module that publishes its coordinate, and what to report. */
internal class CoordinateEdges(private val nameOf: (Module) -> String) {
    fun link(declaration: Declaration, producer: Publication): Outcome {
        val edge = declaration.edge
        val state = VersionState.of(declaration.pinned, producer.version)
        val relationship = Relationship(
            from = edge.from,
            kind = RelationKind.DEPENDS_ON,
            subKind = CoordinateLinks.SUB_KIND,
            resolution = Resolution.Resolved(NodeRef.OfModule(producer.module.id), certaintyOf(edge, producer, state)),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = edge.site,
            scope = edge.scope,
            attributes = attributesOf(declaration, producer.version, state),
        )
        return Outcome(relationship, diagnosticOf(declaration, producer, state))
    }

    private fun certaintyOf(edge: Relationship, producer: Publication, state: VersionState): Certainty {
        val declared = (edge.resolution as? Resolution.Resolved)?.certainty
        val heuristic = state != VersionState.MATCH || declared == Certainty.HEURISTIC ||
            producer.module.certainty == Certainty.HEURISTIC || producer.module.attributes["maven.heuristic"] == "true"
        return if (heuristic) Certainty.HEURISTIC else Certainty.EXACT
    }

    private fun attributesOf(declaration: Declaration, sourceVersion: String, state: VersionState) = listOfNotNull(
        declaration.edge.attributes["configuration"]?.let { "configuration" to it },
        declaration.pinned?.let { "version" to it },
        "sourceVersion" to sourceVersion,
        CoordinateLinks.VERSION_MATCH to state.value,
    ).toMap()

    private fun diagnosticOf(declaration: Declaration, producer: Publication, state: VersionState): Diagnostic? {
        val finding = Finding(nameOf(declaration.consumer), declaration.coordinate, declaration.edge.site)
        val built = BuiltFromSource(nameOf(producer.module), producer.version)
        return when (state) {
            VersionState.MATCH -> null
            VersionState.MISMATCH -> CoordinateTexts.mismatch(finding, checkNotNull(declaration.pinned), built)
            VersionState.UNKNOWN -> CoordinateTexts.unknown(finding, declaration.pinned, built)
        }
    }
}

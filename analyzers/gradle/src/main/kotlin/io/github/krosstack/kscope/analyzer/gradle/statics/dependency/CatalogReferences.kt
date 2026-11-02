package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogReference
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** The reference from a script to the catalog entry it reads. */
internal object CatalogReferences {
    fun relationship(source: ScriptSource, call: ScriptCall, reference: CatalogReference, heuristic: Boolean) =
        Relationship(
            from = NodeRef.OfArtifact(source.artifact.ref),
            kind = RelationKind.REFERENCES,
            subKind = reference.entry.section.symbolKind.removePrefix("gradle."),
            resolution = Resolution.Resolved(NodeRef.OfSymbol(reference.symbol), certainty(heuristic)),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = source.location(call),
            scope = Scope(),
            attributes = mapOf("alias" to reference.entry.accessor, "catalog" to reference.registered.catalog.name),
        )

    fun certainty(heuristic: Boolean): Certainty = if (heuristic) Certainty.HEURISTIC else Certainty.EXACT
}
